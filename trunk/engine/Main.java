/*
 * NewsTerp Engine - We report.  You decipher.
 * copyright (c) 2007 Colin Bayer, Jack Hebert
 *
 * CSE 472 Spring 2007 final project
 */

import java.lang.reflect.Array;

import java.util.*;
import java.io.*;
import java.net.URL;

import opennlp.tools.lang.english.*;

import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.postag.POSDictionary;

public class Main {
	public static void usage() {
		System.err.println(
			"Usage: java Main [-nlp <dir>] [-wn <dir>] [-nnp <n>] <file1>\n" +
				"\t\t<file2> ... <fileN>\n" +
			"\t-nlp <dir>:\t OpenNLP Tools root directory\n" +
			"\t-wn <dir>:\t WordNet dictionary location (absolute path)\n" +
			"\t-nnp <n>: Number of most-popular NPs to print\n"
		);
	}

	public static void main(String[] aArgs) {
		int idx = 0;
		String nlp_path = ".";
		URL wn_path = null;
		int numToShow = 350;
		try {
			wn_path = new URL(
				"file:///usr/temp_store/newsterp/WordNet-3.0/dict");

			String pwd = System.getProperty("user.dir");

			if (pwd != null) {
				wn_path = new File(pwd).toURL();
			}
		} catch (Exception e) {}

		while (idx < aArgs.length) {
			if (aArgs[idx].equals("-nlp")) {
				nlp_path = aArgs[idx + 1];
				idx += 2;
				continue;
			} else if (aArgs[idx].equals("-wn")) {
				try {
					wn_path = new URL(aArgs[idx + 1]);
				} catch (Exception e) {
					System.err.println("Malformed WordNet path URL (" + e + "); exiting...");
					return;
				}
				idx += 2;
				continue;
			} else if (aArgs[idx].equals("-nnp")) {
				numToShow = Integer.parseInt(aArgs[idx+1]);
				idx += 2;
				continue;
			} else if (aArgs[idx].equals("-h")) {
				usage();
				return;
			} else {
				/* idx now points to the first file in the arg list */
				break;
			}
		}

		if (aArgs.length - idx <= 0) {
			/* we need at least one file to extract main ideas from. */
			usage();
			return;
		}

		/* build OpenNLP processing objects */
		if (!NLPToolkitManager.init(
				nlp_path + "/models/english/sentdetect/EnglishSD.bin.gz",
				nlp_path + "/models/english/tokenize/EnglishTok.bin.gz",
				nlp_path + "/models/english/parser/tag.bin.gz",
				nlp_path + "/models/english/parser/tagdict",
				nlp_path + "/models/english/chunker/EnglishChunk.bin.gz",
				wn_path)) {
			System.err.println("Error creating NLP objects, exiting...");
			return;		
		}

		/* provide space to store the processed articles... */
		//TaggedArticle[] articles = new TaggedArticle[aArgs.length - idx];
		ArrayList<TaggedArticle> articleList = new ArrayList<TaggedArticle>();

		/* chop up and tag all of our articles. */
		for (int n = 0; idx < aArgs.length; idx++, n++) {
			try {
			    NewsRepoReader reader = new NewsRepoReader(aArgs[idx]);
			    NewsRepoArticle article = reader.GetNextArticle();
			    int count = 0;
			    while(article != null) {
			    	count += 1;
			    	System.out.println("*****************************");
			    	System.out.println(count+"/"+reader.GetNumberOfArticle());
			    	articleList.add(new TaggedArticle(article.getUrl(), article.getArticle()));
			    	article = reader.GetNextArticle();
			    }
			    //articles[n] = new TaggedArticle(aArgs[idx], aArgs[idx]);
			} catch (IOException e) {}
		}

		// TODO: convert arrayList back to array?

		// do per-article-set fancy stuff here.
		HashMap<TaggedSentence.Chunk, Integer> pop_index = 
			new HashMap<TaggedSentence.Chunk, Integer>();
		RelationExtractor re = new BaselineRelationExtractor();

		int a_i = 0, s_i = 0;

		ArrayList<RelationSet> rel_sets = new ArrayList<RelationSet>();

		for (TaggedArticle a : articleList) {
			s_i = 0;

			RelationSet set = new RelationSet(a.getID());

			for (TaggedSentence s : a.getSentences()) {
				Relation[] r = null;

				if ((r = re.extract(s)) != null && r.length != 0) {
					System.out.println("Extracted relations for article " + a_i + 
						", sentence " + s_i + ": " + Arrays.toString(r));
					for (Relation rel : r) {
						set.add(rel);
					}
				}

				if (s.getChunks(ChunkType.CONJP).length != 0) {
					System.out.println(a_i + ":" + s_i + " (" + s + 
						") has a CONJP.");
				}

				for (TaggedSentence.Chunk ck : s.getChunks(ChunkType.NP)) {
					Integer ck_ct = null;

					if ((ck_ct = pop_index.get(ck)) != null) {
						pop_index.put(ck, new Integer(ck_ct.intValue() + 1));
					} else {
						pop_index.put(ck, new Integer(1));
					}
				}


				s_i++;
			}

			rel_sets.add(set);

			a_i++;
		}

		// don't ask why Java doesn't let you make genericized arrays. just
		// accept that this line works, and move on.
		Map.Entry<TaggedSentence.Chunk, Integer>[] pop_entries =
			(Map.Entry<TaggedSentence.Chunk, Integer>[]) new Map.Entry[0];

		pop_entries = pop_index.entrySet().toArray(pop_entries);

		Arrays.sort(pop_entries, 
			new Comparator< Map.Entry<TaggedSentence.Chunk, Integer> > () {
				public int 
					compare(Map.Entry<TaggedSentence.Chunk, Integer> aA,
							Map.Entry<TaggedSentence.Chunk, Integer> aB) {
					return aA.getValue().compareTo(aB.getValue());
				}
			}
		);

		System.out.println("Most popular " + numToShow + 
			" NPs in article set:");
		
		int lowerBound = Math.max(0, pop_entries.length - 1 - numToShow);
		for (int i = pop_entries.length - 1; i > lowerBound; i--) {
			System.out.println(pop_entries[i].getKey() + " (" + 
				pop_entries[i].getValue() + ")");
		}

		// dump relation sets to a file.
		try {
			FileWriter rsf = new FileWriter("relations.dat");

			for (RelationSet rs : rel_sets) {
				rsf.write(rs.toSerialRep());
			}

			rsf.write("\n");

			rsf.close();
		} catch (IOException e) {
			System.err.println("Warning: couldn't dump relation sets...");
		}
	}
}
