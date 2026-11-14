import java.io.*; import java.nio.file.*; import java.util.regex.*;
import javax.xml.parsers.*; import org.w3c.dom.*;
import org.apache.velocity.runtime.RuntimeInstance;
public class ParseCheck {
  public static void main(String[] a) throws Exception {
    RuntimeInstance ri = new RuntimeInstance(); ri.init();
    int bad = 0;
    for (String f : a) {
      Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(f));
      NodeList nl = d.getElementsByTagName("content");
      for (int i = 0; i < nl.getLength(); i++) {
        String c = nl.item(i).getTextContent();
        Matcher m = Pattern.compile("\\{\\{velocity[^}]*\\}\\}(.*?)\\{\\{/velocity\\}\\}", Pattern.DOTALL).matcher(c);
        int n = 0;
        while (m.find()) { n++;
          try { org.apache.velocity.Template t = new org.apache.velocity.Template(); t.setName(f + "#" + n); ri.parse(new StringReader(m.group(1)), t); }
          catch (Exception e) { bad++; System.out.println("FAIL " + f + " block " + n + ": " + e.getMessage()); }
        }
        if (n > 0) System.out.println("checked " + n + " block(s) in " + f + " (content #" + i + ")");
      }
    }
    System.out.println(bad == 0 ? "ALL PARSE OK" : bad + " FAILURES");
    System.exit(bad == 0 ? 0 : 1);
  }
}
