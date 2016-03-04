package org.rcsb.mmtf.mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.vecmath.Point3d;

import org.apache.spark.api.java.function.PairFlatMapFunction;
import org.rcsb.mmtf.dataholders.CalphaAlignBean;
import org.rcsb.mmtf.dataholders.PDBGroup;
import org.rcsb.mmtf.decoder.DecodeStructure;

import scala.Tuple2;

/**
 * Method to strip down the chain to just the polymer (remove ligands) - and return multiple chains
 * @author Anthony Bradley
 *
 */
public class ChainStripper implements PairFlatMapFunction<Tuple2<String,DecodeStructure>, String, CalphaAlignBean>{

	private int groupCounter;
	private int atomCounter;
	private int[] groupList;
	private int[] groupsPerChain;
	private int[] cartnX;
	private int[] cartnY;
	private int[] cartnZ;
	private Map<Integer, PDBGroup> groupMap;
	String[] chainList;
	private List<String> calphaArr;
	private List<String> dnarnaArr;
	private List<String> sequenceList;
	private int[] seqResGroupList;
	private static final long serialVersionUID = -8516822489889006992L;

	@Override
	public Iterable<Tuple2<String, CalphaAlignBean>> call(Tuple2<String, DecodeStructure> t) throws Exception {
		// Loop through the data structure and output a new one - on a per chain level
		// The out array to produce
		List<Tuple2<String, CalphaAlignBean>> outArr = new ArrayList<Tuple2<String, CalphaAlignBean>>();
		DecodeStructure decodeStructure= t._2;
		// Get the coordinates
		cartnX =  decodeStructure.getCartnX();
		cartnY = decodeStructure.getCartnY();
		cartnZ = decodeStructure.getCartnZ();
		groupMap = decodeStructure.getGroupMap();
		chainList = decodeStructure.getInternalChainIds();
		// Loop through the chains
		groupCounter = 0;
		atomCounter = 0;
		groupList = decodeStructure.getGroupList();
		groupsPerChain = decodeStructure.getGroupsPerChain();
		// The list of sequence info for each chain
		sequenceList = decodeStructure.getSequenceInfo();
		seqResGroupList = decodeStructure.getSeqResGroupList();
		int sum = 0;
		for( int grpChain : groupsPerChain){
			sum+=grpChain;
		}
		System.out.println(seqResGroupList.length + " vs " + sum);
		int numChains = decodeStructure.getChainsPerModel()[0];
		// Now set the requirements for a calpha group
		calphaArr = new ArrayList<String>();
		calphaArr.add("C");
		calphaArr.add("CA");
		dnarnaArr = new ArrayList<String>();
		dnarnaArr.add("P");
		dnarnaArr.add("P");
		
		// GENERATE THe ARRAYS TO OUTPUT		
		for (int i=0; i<numChains;i++){
			CalphaAlignBean outChain;
			try{
			outChain = setInfoForChain(i, t._1, chainList[i]);
			outArr.add(new Tuple2<String, CalphaAlignBean>(outChain.getPdbId(), outChain));
			}
			catch(Exception e){
				System.out.println("ERROR WITH "+t._1+" CHAIN: "+chainList[i]);
				e.printStackTrace();
			}
		}
		return  outArr;
	}

	/**
	 * Set the calpha/phosphate information for this chain.
	 * @param currentChainIndex The index for this chain
	 * @param pdbId The structure's pdb id (four char string)
	 * @param chainId The chain identifier (string up to four chars)
	 * @return The generated data
	 */
	private CalphaAlignBean setInfoForChain(int currentChainIndex, String pdbId, String chainId) {
		boolean peptideFlag = false;
		boolean dnaRnaFlag = false;
		CalphaAlignBean outChain = new CalphaAlignBean();
		int groupsThisChain = groupsPerChain[currentChainIndex];
		List<Point3d> thesePoints = new ArrayList<Point3d>();
		int[] currChainSeqToGroupMap = new int[groupsThisChain];
 		for(int j=0; j<groupsThisChain;j++){
			int g = groupList[groupCounter];
			// Get the value for this group - that indicates where this group sits on the sequence.
			currChainSeqToGroupMap[groupCounter] = seqResGroupList[groupCounter];
			// Now increment the groupCounter
			groupCounter++;
			PDBGroup thisGroup = groupMap.get(g);
			List<String> atomInfo = thisGroup.getAtomInfo();
			// Now check - this is protein / DNA or RNA
			int atomCount = atomInfo.size()/2;
			if(atomCount<2){
				if(atomInfo.equals(calphaArr)==false && atomInfo.equals(dnarnaArr)==false){
					atomCounter+=atomCount;
					continue;
				}
			}
			else{
				atomCounter+=atomCount;
				continue;
			}
			if(atomInfo.equals(calphaArr)==true){
				peptideFlag=true;
			}
			if(atomInfo.equals(dnarnaArr)==true){
				dnaRnaFlag=true;
			}
			for(int k=0;k<atomCount;k++){
				Point3d newPoint = new Point3d(); 
				newPoint.x = cartnX[atomCounter+k]/1000.0;
				newPoint.y = cartnY[atomCounter+k]/1000.0;
				newPoint.z = cartnZ[atomCounter+k]/1000.0;
				thesePoints.add(newPoint);
			}
			atomCounter+=atomCount;
		}
		// Set data for Chain
		outChain.setPdbId(pdbId);
		outChain.setChainId(chainId);
		outChain.setCoordList(thesePoints.toArray(new Point3d[thesePoints.size()]));
		outChain.setSequence(sequenceList.get(currentChainIndex));
		if(peptideFlag==true){
			if(dnaRnaFlag==true){
				outChain.setPolymerType("NUCLEOTIDE_PEPTIDE");

			}
			else{
				outChain.setPolymerType("PEPTIDE");

			}
		}
		else if(dnaRnaFlag==true){
			outChain.setPolymerType("NUCLEOTIDE");
		}
		return outChain;
	}


}
