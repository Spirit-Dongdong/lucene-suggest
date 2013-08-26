package org.apache.lucene.search.mytest;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import org.apache.lucene.search.suggest.TermFreq;
import org.apache.lucene.search.suggest.fst.FSTCompletion;
import org.apache.lucene.search.suggest.fst.FSTCompletionBuilder;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util._TestUtil;


public class FSTTest {
  
  private static FSTCompletion completion;
  private static FSTCompletion completionAlphabetical;
  
  public static TermFreq tf(String t, int v) {
    return new TermFreq(t, v);
  }
  
  static final TermFreq[] keys = new TermFreq[] {
//      tf("one", 0),
//      tf("oneness", 1),
//      tf("onerous", 1),
//      tf("onesimus", 1),
//      tf("two", 1),
//      tf("twofold", 1),
//      tf("twonk", 1),
//      tf("thrive", 1),
//      tf("through", 1),
//      tf("threat", 1),
//      tf("three", 1),
//      tf("foundation", 1),
//      tf("fourblah", 1),
//      tf("fourteen", 1),
      tf("four", 0),
      tf("fourier", 0),
      tf("fourty", 0),
//      tf("xo", 1),
    };
  
    static {
      FSTCompletionBuilder builder = new FSTCompletionBuilder();
      for (TermFreq tf : keys) {
        try {
          builder.add(tf.term, (int) tf.v);
        } catch (IOException e) {
          throw new RuntimeException();
        }
      }
      try {
        completion = builder.build();
      } catch (IOException e) {
        throw new RuntimeException();
      }
      completionAlphabetical = new FSTCompletion(completion.getFST(), false, true);
    }
    
    private static CharSequence stringToCharSequence(String prefix) {
      return _TestUtil.stringToCharSequence(prefix, random());
    }
    
    private static Random random() {
      return new Random();
    }
    
    public static void displayByteRef(IntsRef input) {
      int len = input.length;
      StringBuilder sb = new StringBuilder();
      System.out.println("bucket:" + input.ints[len - 1]);
      for (int i = 0; i < len - 1; i++) {
          char c = (char) input.ints[i];
          sb.append(c).append(" ");
      }
      System.out.println(sb);
    }

    public static void main(String[] args) {
      CharSequence target = stringToCharSequence("fouri");
      List result = completion.lookup(target, 5);
      
      System.out.println(result);
    }
}
