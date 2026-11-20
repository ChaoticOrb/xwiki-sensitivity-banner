import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringWriter;

public class SheetGuardTest {
    public static class Esc { public String xml(Object o) { return String.valueOf(o); } }
    public static class Csrf { public String getToken() { return "TOK"; } }
    public static class Svc { public Csrf csrf = new Csrf(); public Csrf getCsrf() { return csrf; } }
    public static class Xw {
        boolean core; Xw(boolean c) { core = c; }
        public boolean exists(String ref) { return core && ref.equals("SensitivityBanner.Code.Common"); }
        public String getURL(String page, String action, String q) { return "/bin/" + action + "/" + page.replace('.', '/') + "?" + q; }
    }
    static int failures = 0;
    static void check(boolean c, String m) { if (!c) { failures++; System.out.println("FAIL: " + m); } else System.out.println("ok: " + m); }
    static String render(String script, boolean core) throws Exception {
        VelocityEngine ve = new VelocityEngine(); ve.init();
        VelocityContext ctx = new VelocityContext();
        ctx.put("xwiki", new Xw(core)); ctx.put("escapetool", new Esc()); ctx.put("services", new Svc());
        StringWriter w = new StringWriter(); ve.evaluate(ctx, w, "sheet", script); return w.toString();
    }
    public static void main(String[] a) throws Exception {
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(a[0]));
        String c = d.getElementsByTagName("content").item(0).getTextContent();
        int open = c.indexOf("{{velocity}}") + "{{velocity}}".length();
        String script = c.substring(open, c.indexOf("{{/velocity}}", open));
        String without = render(script, false);
        check(without.contains("is not installed") && without.contains("Import the Sensitivity Banner XAR first"), "without the core the sheet shows the not installed message");
        check(!without.contains("id=\"sb-mg\"") && !without.contains("sb-mg-scan-m"), "without the core none of the migration buttons or inputs are rendered");
        String with = render(script, true);
        check(!with.contains("is not installed"), "with the core there is no error message");
        check(with.contains("id=\"sb-mg\"") && with.contains("sb-mg-scan-m") && with.contains("sb-mg-apply-c"), "with the core the inputs and buttons are rendered");
        check(with.contains("data-url=\"/bin/get/SensitivityMigration/Code/Handler?outputSyntax=plain\""), "the page talks to the handler in the migration space: " + with.replaceAll("(?s).*data-url=\"([^\"]*)\".*", "$1"));
        check(with.contains("data-token=\"TOK\"") && with.contains("temporary tool"), "the token and the temporary tool notice are present");
        System.out.println(failures == 0 ? "\nALL PASSED" : "\n" + failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
