package sprinkler;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.print.attribute.standard.Finishings;

import sprinkler.data.Node;
import sprinkler.data.NominalRecord;
import sprinkler.data.RecordSet;
import sprinkler.data.TestCondition;
import sprinkler.data.Tree;
import sprinkler.purity.Gini;
import sprinkler.utilities.Bundle;
import sprinkler.utilities.CSVLoader;
import vivin.*;

/**
 * @author Claudio Tanci
 * This class implements a generic Hunt algorithm for decision tree growing
 * 
 */
public class GenericHunt {

	/**
	 * stoppingCondition has the stopping condition been reached? 
	 * @param Node
	 * @param purity required
	 * @return boolean
	 */
	private static boolean stoppingCondition(Node node, float purity) {

		if (node.getPurity() <= (float) purity) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * label a node
	 * @param Node
	 * @return label to be assigned to the node
	 */
	private static String label(Node node) {
		// the node label is the most recurrent label of its records
		RecordSet records = node.getRecords();

		ArrayList<String> list = new ArrayList<String>();
		
		for (NominalRecord record : records.getRecords()) {
			list.add(record.getLabel().toString());
		}

		// computing the labels list
		HashSet<String> labels = new HashSet<String>();

		Iterator<String> it = list.iterator();
		while (it.hasNext()) {
			String label = it.next();
			labels.add(label);
		}

		int max = 0;
		String classLabel = "";

		for (String label : labels) {
			int freq = Collections.frequency(list, label);

			if (freq > max) {
				max = freq;
				classLabel = label;
			}
		}

		return classLabel;
	}

	/**
	 * findBestSplit select the most appropriate attribute to split
	 * NB. only nominal attributes supported
	 * @param Node
	 * @param attributes
	 * @return TestCondition
	 */
	private static TestCondition findBestSplit(Node node, ArrayList<Integer> attributes) {
		
		/* 
		 * implements a multiway split
		 */
		int size = node.getRecords().size();
		
		int bestSplit = 0;
		float bestAvg = 1;
		for (Integer attribute : attributes) {
			
			// domain value for the attribute i
			ArrayList<String> values = node.getRecords().getRecord(0).getAttribute(attribute).getDomain();
			
			// computing the weighted average index
			float avg = 0;
			
			for (String value : values) {
				Node tentativeSplit = split(node, attribute, value);

				float valuePurity = (float) tentativeSplit.getPurity();
				int valueNumber = tentativeSplit.size();

				avg = (float) avg + (float) valueNumber / (float) size
						* valuePurity;
			}
			
			if (avg <= bestAvg) {
				bestAvg = avg;
				bestSplit = attribute;
			}
		}
				
		TestCondition testCondition = new TestCondition();
		testCondition.setIdAttribute(bestSplit);

		ArrayList<String> tobesplittedValue = new ArrayList<String>();
		
		ArrayList<String> values = node.getRecords().getRecord(0).getAttribute(bestSplit).getDomain();
		for (String value : values) {
			int a = node.howMany(bestSplit, value);
			if (a > 0) {
				tobesplittedValue.add(value);
			}
		}

		String[] temp = new String[tobesplittedValue.size()];
		temp = tobesplittedValue.toArray(temp);

		testCondition.setValues(temp);

		return testCondition;
	}

	/**
	 * split a node
	 * @param parent node to be splitted
	 * @param attribute
	 * @param value
	 * @return a node with all the records with attribute = value
	 */
	private static Node split(Node node, int attribute, String value) {
		
		RecordSet newRecordSet = new RecordSet();
		
		for (NominalRecord record : node.getRecords().getRecords()) {
			if (record.getAttribute(attribute).toString().equals(value)) {
				newRecordSet.add(record);
			}			
		}

		Node newNode = new Node(newRecordSet);
		return newNode;
	}

	/**
	 * treeGrowth build the decision tree (this is the first call)
	 * @param tree
	 * @param purity
	 * @return tree
	 */
	public static Tree treeGrowth(Tree tree, float purity) {
		Node node = (Node) tree.getRoot();
		// number of attributes
		int numberOfAttributes = node.getRecords().getRecord(0).getAttributes().size();
		ArrayList<Integer> attributes = new ArrayList<Integer>();
		for (int i = 0; i < numberOfAttributes; i++) {
			attributes.add(i);
		}
		
		return treeGrowth(tree, purity, attributes);
	}
	
		/**
		 * treeGrowth build the decision tree
		 * @param tree
		 * @param purity
		 * @param attributes to consider for the split
		 * @return tree
		 */
		public static Tree treeGrowth(Tree tree, float purity, ArrayList<Integer> attributes) {		
		
		Node node = (Node) tree.getRoot();

		// setting the label for both leaf and not leaf nodes
		node.setLabel(label(node));

		if (stoppingCondition(node, purity) ) {
			// node is a leaf
			node.setLeaf(true);
		} else {
			// splitting the node
			TestCondition bestSplit = findBestSplit(node, attributes);
			
			for (String value : bestSplit.getValues()) {

				Node child = split(node, bestSplit.getIdAttribute(), value);
				node.addChild(child);
				
				// ok only for single value split!
				String[] values = { value };

				TestCondition testCondition = new TestCondition();
				testCondition.setValues(values);
				testCondition.setIdAttribute(bestSplit.getIdAttribute());

				child.setTestCondition(testCondition);
			}
			
			// flush records from parent for memory issues
//			node.setRecords(null);
			
			// remove splitted attribute
			ArrayList<Integer> childAttributes = (ArrayList<Integer>) attributes.clone();
			childAttributes.remove(attributes.indexOf(bestSplit.getIdAttribute()));
			
			for (GenericTreeNode<Node> child : node.getChildren()) {
				Tree treeChild = new Tree();
				treeChild.setRoot(child);

				ArrayList<Integer> childAttr = (ArrayList<Integer>) childAttributes.clone();
				treeGrowth(treeChild, purity, childAttr);
			}
		}
		
		return tree;
	}

	/**
	 * main
	 * @param args
	 */
	public static void main(String[] args) {
//		test();
//		testSplit();
//		testHowMany();
//		testClassify();
	}
			
	public static void test() {
		
		float purity = (float) 0.1;

		// record set
		//String strFile = Bundle.getString("Resources.RecordSet"); //$NON-NLS-1$
		String strFile = "./data/connect4/connect4.data";
		Node root = new Node();

		System.out.println("Reading file data " + strFile);
		try {
			root.setRecords(CSVLoader.loadRecordSet(strFile));
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		System.out.println("Generating decision tree...");
		Tree tree = new Tree();
		tree.setRoot(root);

		System.out.println(((Node) tree.getRoot()).size() + " records to be analyzed");
		
		tree = (Tree) treeGrowth(tree, purity);

		System.out.println(tree.getNumberOfNodes() + " nodes in the tree");
		
		System.out.println("Cleaning data set records...");
		tree.clean();
		
		// saving the tree
		try {
			String fileName = Bundle.getString("Resources.SaveFileName");
			tree.save(fileName);
			System.out.println("Decision tree saved as "+fileName);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// exporting tree dot file
		tree.toDot(Bundle.getString("Resources.DotFileName"));
	}

	/**
	 * testSplit test a node splitting
	 */
	private static void testSplit() {

		String strFile = Bundle.getString("Resources.RecordSet"); //$NON-NLS-1$

		Node root = new Node();
		try {
			root.setRecords(CSVLoader.loadRecordSet(strFile));
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("Split test for attribute 0");

		String[] values = { "x", "o", "b" };

		int tot = 0;
		for (String value : values) {

			Node node = split(root, 0, value);

			System.out.println("value " + value + " " + node.size()
					+ ", purity " + node.getPurity());
			tot = tot + node.getRecords().size();
		}

		System.out.println("____________");
		System.out.println("records " + tot);
	}

	/**
	 * testHowMany
	 */
	private static void testHowMany() {

		String strFile = Bundle.getString("Resources.RecordSet"); //$NON-NLS-1$

		Node root = new Node();
		try {
			root.setRecords(CSVLoader.loadRecordSet(strFile));
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.out.println("HowMany for attribute=0 test");

		String[] values = { "x", "o", "b" };

		int tot = 0;
		for (String value : values) {
			System.out.println("value " + value + " " + root.howMany(0, value));
		}

		tot = tot + root.getRecords().size();

		System.out.println("____________");
		System.out.println("records " + tot);

	}

	/**
	 * testClassify
	 */
	private static void testClassify() {

		String strFile = Bundle.getString("Resources.RecordSet"); //$NON-NLS-1$

		Node root = new Node();
		try {
			root.setRecords(CSVLoader.loadRecordSet(strFile));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println("Classify test");
		System.out.println("Node label: " + label(root));
	}
}