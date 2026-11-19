import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringWriter;
import java.util.*;

/** Runs the real handler Velocity under Velocity 2.3 against stand-ins for the XWiki objects. */
public class HandlerHarness {
    static final ObjectMapper JSON = new ObjectMapper();
    static int failures = 0;
    static final String CLASS = "SensitivityBanner.Code.SensitivityClass";

    // ---- stand-ins
    public static class Obj {
        public Map<String, String> v = new HashMap<>();
        public String getValue(String f) { return v.get(f); }
        public Object set(String f, Object val) { v.put(f, String.valueOf(val)); return null; }
    }
    public static class Doc {
        public String content; public boolean canEdit = true; public Obj obj; public List<String> saves = new ArrayList<>();
        public String getContent() { return content; }
        public Object setContent(String c) { content = c; return null; }
        public boolean hasAccessLevel(String l) { return canEdit; }
        public List<Obj> getObjects(String cls) { return obj == null ? new ArrayList<>() : new ArrayList<>(List.of(obj)); }
        public Obj getObject(String cls, boolean create) { if (obj == null && create) obj = new Obj(); return obj; }
        public void save(String comment, boolean minor) { saves.add(comment); }
        public Obj getObject(String cls) { return obj; }
        public String name = "Space.Page";
        public List<String> revs = new ArrayList<>(); public Map<String, Doc> revDocs = new HashMap<>();
        public String[] getRecentRevisions(int n) { return revs.subList(0, Math.min(n, revs.size())).toArray(new String[0]); }
        public String getURL(String a) { return "/" + name + "/" + a; }
        public boolean isNew() { return false; }
        public Ref getDocumentReference() { return new Ref(name); }
    }
    public static class Ref {
        String n; Ref(String n) { this.n = n; }
        public String getName() { return n.substring(n.lastIndexOf('.') + 1); }
        public List<Ref> getSpaceReferences() { return List.of(new Ref(n.substring(0, n.indexOf('.')))); }
    }
    static Map<String, Doc> store;
    public static class Xwiki {
        public boolean admin = true;
        public boolean hasAdminRights() { return admin; }
        public boolean exists(Object ref) { return store.containsKey(String.valueOf(ref)); }
        public Doc getDocument(Object ref) { return store.get(String.valueOf(ref)); }
        public Doc getDocument(Doc d, String rev) { return d.revDocs.get(rev); }
    }
    public static class Model { public Object resolveDocument(String s) { return s; } }
    public static class Csrf { public boolean isTokenValid(Object t) { return "GOOD".equals(String.valueOf(t)); } }
    public static class Query {
        String like = "";
        public Query bindValue(String n, Object v) { like = String.valueOf(v).replace("%", "").toLowerCase(); return this; }
        public Query setLimit(int n) { return this; }
        public Query setOffset(int n) { return this; }
        public List<String> execute() {
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, Doc> e : store.entrySet()) if (e.getValue().content.toLowerCase().contains(like)) out.add(e.getKey());
            Collections.sort(out); return out;
        }
    }
    public static class QueryService { public Query xwql(String s) { return new Query(); } }
    public static class Services {
        public Csrf csrf = new Csrf(); public Model model = new Model(); public QueryService query = new QueryService();
        public Csrf getCsrf() { return csrf; } public Model getModel() { return model; } public QueryService getQuery() { return query; }
    }
    public static class Json {
        public String serialize(Object o) throws Exception { return JSON.writeValueAsString(o); }
        public Object fromString(String s) { try { return JSON.readValue(s, Object.class); } catch (Exception e) { return null; } }
    }
    public static class Req extends HashMap<String, Object> {
        public String method = "POST"; public String getMethod() { return method; }
        public Map<String, String[]> getParameterMap() { Map<String, String[]> m = new HashMap<>(); for (Map.Entry<String, Object> e : entrySet()) m.put(e.getKey(), new String[] { String.valueOf(e.getValue()) }); return m; }
    }
    public static class XCtx extends HashMap<String, Object> { public boolean finished; public void setFinished(boolean b) { finished = b; } }
    public static class Resp {
        public int status = 200; public String type = "";
        public void setContentType(String t) { type = t; } public void setStatus(int s) { status = s; }
        public void sendError(int s, String m) { status = s; }
        public String redirect = ""; public void sendRedirect(String u) { redirect = u; status = 302; }
    }
    public static class MathT { public Number add(Object a, Object b) { return ((Number) a).intValue() + ((Number) b).intValue(); } }

    static String script(String xmlPath) throws Exception {
        Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(xmlPath));
        String c = d.getElementsByTagName("content").item(0).getTextContent();
        int open = c.indexOf("{{velocity");
        int a = c.indexOf("}}", open) + 2;
        return c.substring(a, c.indexOf("{{/velocity}}", a));
    }

    static JsonNode run(String script, boolean nullAllowed, boolean admin, String method, Map<String, Object> params, int[] statusOut) throws Exception {
        VelocityEngine ve = new VelocityEngine();
        ve.setProperty("directive.set.null.allowed", nullAllowed);
        ve.init();
        VelocityContext ctx = new VelocityContext();
        Xwiki xw = new Xwiki(); xw.admin = admin;
        Req req = new Req(); req.method = method; req.putAll(params); req.put("outputSyntax", "plain");
        Resp resp = new Resp();
        XCtx xctx = new XCtx(); xctx.put("action", "get"); xctx.put("database", "xwiki");
        Map<String, Object> opts = new HashMap<>();
        opts.put("owner", List.of(opt("Management"), opt("Senior Management"), opt("Standard Staff")));
        opts.put("security", List.of(opt("Sensitive"), opt("Open")));
        opts.put("country", List.of(opt("UK"), opt("Europe")));
        ctx.put("xcontext", xctx); ctx.put("xwiki", xw); ctx.put("services", new Services()); ctx.put("jsontool", new Json());
        ctx.put("request", req); ctx.put("response", resp); ctx.put("mathtool", new MathT());
        ctx.put("sbFields", List.of("owner", "security", "country")); ctx.put("sbOptions", opts); ctx.put("sbClassName", CLASS);
        StringWriter w = new StringWriter();
        ve.evaluate(ctx, w, "handler", script);
        statusOut[0] = resp.status; lastRedirect = resp.redirect;
        String out = w.toString().trim();
        return out.isEmpty() ? null : JSON.readTree(out);
    }
    static String lastRedirect = "";
    /** Runs the Common block then the save handler block, as the wiki does through the include. */
    static void runSave(String common, String save, boolean nullAllowed, Doc current, Map<String, Object> params, int[] statusOut) throws Exception {
        VelocityEngine ve = new VelocityEngine();
        ve.setProperty("directive.set.null.allowed", nullAllowed);
        ve.init();
        VelocityContext ctx = new VelocityContext();
        Xwiki xw = new Xwiki();
        Req req = new Req(); req.putAll(params); req.put("outputSyntax", "plain");
        Resp resp = new Resp(); XCtx xctx = new XCtx(); xctx.put("action", "get");
        ctx.put("xcontext", xctx); ctx.put("xwiki", xw); ctx.put("services", new Services()); ctx.put("jsontool", new Json());
        ctx.put("request", req); ctx.put("response", resp); ctx.put("mathtool", new MathT());
        ctx.put("escapetool", new Object()); ctx.put("doc", current);
        ve.evaluate(ctx, new StringWriter(), "common", common);
        ve.evaluate(ctx, new StringWriter(), "save", save);
        statusOut[0] = resp.status; lastRedirect = resp.redirect;
    }
    static Map<String, Object> opt(String label) { Map<String, Object> m = new HashMap<>(); m.put("label", label); m.put("colour", ""); return m; }

    static void check(boolean c, String m) { if (!c) { failures++; System.out.println("FAIL: " + m); } else System.out.println("ok: " + m); }

    static Doc doc(String content, String owner, String security, String country, boolean canEdit) {
        Doc d = new Doc(); d.content = content; d.canEdit = canEdit;
        if (owner != null || security != null || country != null) {
            d.obj = new Obj(); d.obj.v.put("owner", owner == null ? "" : owner); d.obj.v.put("security", security == null ? "" : security); d.obj.v.put("country", country == null ? "" : country);
        }
        return d;
    }
    static Map<String, Object> row(String page, String o, String s, String c, String... remove) {
        Map<String, Object> m = new HashMap<>(); m.put("page", page); m.put("owner", o); m.put("security", s); m.put("country", c);
        if (remove.length > 0) m.put("remove", List.of(remove));
        return m;
    }
    static Map<String, Object> p(Object... kv) { Map<String, Object> m = new HashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]); return m; }
    static String j(Object o) throws Exception { return JSON.writeValueAsString(o); }

    public static void main(String[] a) throws Exception {
        String migrate = script(a[0]);
        String restore = script(a[1]);
        String backup = script(a[2]);
        String common = script(a[3]);
        String save = script(a[4]);
        for (boolean nullAllowed : new boolean[] { false, true }) {
            System.out.println("\n=== directive.set.null.allowed=" + nullAllowed + " ===");
            String tag = "[null=" + nullAllowed + "] ";
            int[] st = new int[1];
            // access control
            store = new HashMap<>();
            JsonNode r = run(migrate, nullAllowed, false, "GET", p("op", "config"), st);
            check(st[0] == 403 && r.has("error"), tag + "a non admin is refused");
            r = run(migrate, nullAllowed, true, "GET", p("op", "content"), st);
            check(st[0] == 405, tag + "content needs POST");
            r = run(migrate, nullAllowed, true, "POST", p("op", "apply", "form_token", "BAD", "mode", "dryrun", "rows", "[]"), st);
            check(st[0] == 403 && r.get("error").asText().contains("token"), tag + "a bad token is refused");
            r = run(migrate, nullAllowed, true, "POST", p("op", "nonsense", "form_token", "GOOD"), st);
            check(st[0] == 400, tag + "an unknown operation is refused");
            r = run(migrate, nullAllowed, true, "GET", p("op", "config"), st);
            check(st[0] == 200 && r.get("options").get("owner").size() == 3 && r.get("options").get("security").get(1).asText().equals("Open"), tag + "config returns the option lists");

            // list and content: the page without an object must not inherit the previous page's object
            store = new LinkedHashMap<>();
            store.put("Space.WithObject", doc("{{sensitivity owner=\"Management\"/}}", "Management", "Open", "UK", true));
            store.put("Space.NoObject1", doc("{{sensitivity owner=\"Standard Staff\"/}} body", null, null, null, true));
            store.put("Space.NoObject2", doc("another sensitivity mention", null, null, null, false));
            store.put("SensitivityBanner.Code.X", doc("sensitivity internal", null, null, null, true));
            r = run(migrate, nullAllowed, true, "GET", p("op", "list", "macro", "sensitivity"), st);
            check(r.get("total").asInt() == 4, tag + "list returns the candidate pages (the harness query does not apply the space exclusion)");
            r = run(migrate, nullAllowed, true, "GET", p("op", "list", "macro", "bad name!"), st);
            check(st[0] == 400, tag + "list refuses an invalid macro name");
            r = run(migrate, nullAllowed, true, "POST", p("op", "content", "form_token", "GOOD", "names", j(List.of("Space.WithObject", "Space.NoObject1", "Space.NoObject2", "Space.Gone"))), st);
            JsonNode pages = r.get("pages");
            check(pages.size() == 3, tag + "content skips a page that does not exist");
            check(pages.get(0).get("values").get("owner").asText().equals("Management") && pages.get(0).get("values").get("country").asText().equals("UK"), tag + "content returns stored values for a page with an object");
            check(pages.get(1).get("values").get("owner").asText().isEmpty() && pages.get(2).get("values").get("owner").asText().isEmpty(), tag + "pages without an object report empty values, they do not inherit the previous page's object");
            check(pages.get(1).get("canEdit").asBoolean() && !pages.get(2).get("canEdit").asBoolean() && pages.get(1).get("content").asText().contains("body"), tag + "content returns rights and the source");

            // apply (migration)
            store = new LinkedHashMap<>();
            store.put("S.Ready", doc("x", null, null, null, true));
            store.put("S.Has", doc("x", "Management", null, null, true));
            store.put("S.NoRights", doc("x", null, null, null, false));
            store.put("S.AfterObj", doc("x", "Open", null, null, true)); // has object, so a following page without one tests inheritance
            store.put("S.ReadyToo", doc("x", null, null, null, true));
            List<Object> rows = new ArrayList<>(List.of(
                row("S.Ready", "Management", "Sensitive", "UK"),
                row("S.Has", "Management", "", ""),
                row("S.NoRights", "Management", "", ""),
                row("S.Missing", "Management", "", ""),
                row("S.Ready", "Interns", "", ""),
                row("S.Ready", "", "", ""),
                row("S.AfterObj", "Management", "", ""),
                row("S.ReadyToo", "", "Open", "")));
            r = run(migrate, nullAllowed, true, "POST", p("op", "apply", "form_token", "GOOD", "mode", "dryrun", "rows", j(rows)), st);
            JsonNode c = r.get("counts");
            check(c.get("ready").asInt() == 2 && c.get("hasvalues").asInt() == 2 && c.get("norights").asInt() == 1 && c.get("missing").asInt() == 1 && c.get("invalid").asInt() == 2 && c.get("applied").asInt() == 0, tag + "migration dry run counts: " + c);
            check(store.get("S.Ready").obj == null && store.get("S.ReadyToo").obj == null && store.get("S.Ready").saves.isEmpty(), tag + "the dry run wrote nothing");
            r = run(migrate, nullAllowed, true, "POST", p("op", "apply", "form_token", "GOOD", "mode", "apply", "rows", j(rows)), st);
            c = r.get("counts");
            check(c.get("applied").asInt() == 2, tag + "migration applied 2 pages: " + c);
            check("Management".equals(store.get("S.Ready").obj.v.get("owner")) && "Sensitive".equals(store.get("S.Ready").obj.v.get("security")) && "UK".equals(store.get("S.Ready").obj.v.get("country")), tag + "page S.Ready has all three values");
            check("Open".equals(store.get("S.ReadyToo").obj.v.get("security")) && "".equals(store.get("S.ReadyToo").obj.v.get("owner")), tag + "page S.ReadyToo got the right values and nothing from the previous page");
            check("Open".equals(store.get("S.AfterObj").obj.v.get("owner")) && "Management".equals(store.get("S.Has").obj.v.get("owner")), tag + "pages that already had values were not changed");
            check(store.get("S.NoRights").obj == null && store.get("S.Ready").saves.size() == 1 && store.get("S.Ready").saves.get(0).contains("Migrated"), tag + "no rights page untouched, one save with a comment");
            r = run(migrate, nullAllowed, true, "POST", p("op", "apply", "form_token", "GOOD", "mode", "apply", "rows", "not json"), st);
            check(st[0] == 400, tag + "malformed rows are refused");
            r = run(migrate, nullAllowed, true, "POST", p("op", "apply", "form_token", "GOOD", "mode", "apply", "rows", j(Collections.nCopies(101, row("S.Ready", "Management", "", "")))), st);
            check(st[0] == 400, tag + "more than 100 rows are refused");

            // clean (macro removal)
            String m1 = "{{sensitivity owner=\"Management\" security=\"Sensitive\" country=\"UK\"/}}\n";
            String m2 = "{{sensitivity owner=\"Open\"/}}\n";
            store = new LinkedHashMap<>();
            store.put("C.Ok", doc(m1 + "Body", "Management", "Sensitive", "UK", true));
            store.put("C.Differs", doc(m1 + "Body", "Management", "Open", "UK", true));
            store.put("C.NotFound", doc("Body without the macro", "Management", "Sensitive", "UK", true));
            store.put("C.NoObj", doc(m1 + "Body", null, null, null, true));
            store.put("C.NoRights", doc(m1 + "Body", "Management", "Sensitive", "UK", false));
            store.put("C.Twice", doc(m1 + "Mid\n" + m1 + "End", "Management", "Sensitive", "UK", true));
            store.put("C.Next", doc(m1 + "Next", "Management", "Sensitive", "UK", true));
            List<Object> crows = new ArrayList<>();
            crows.add(row("C.Ok", "Management", "Sensitive", "UK", m1));
            crows.add(row("C.Differs", "Management", "Sensitive", "UK", m1));
            crows.add(row("C.NotFound", "Management", "Sensitive", "UK", m1));
            crows.add(row("C.NoObj", "Management", "Sensitive", "UK", m1));
            crows.add(row("C.NoRights", "Management", "Sensitive", "UK", m1));
            crows.add(row("C.Twice", "Management", "Sensitive", "UK", m1));
            crows.add(row("C.Missing", "Management", "Sensitive", "UK", m1));
            Map<String, Object> noRemove = row("C.Next", "Management", "Sensitive", "UK"); // no remove key after rows that had one
            crows.add(noRemove);
            r = run(migrate, nullAllowed, true, "POST", p("op", "clean", "form_token", "GOOD", "mode", "dryrun", "rows", j(crows)), st);
            c = r.get("counts");
            check(c.get("removable").asInt() == 2 && c.get("differs").asInt() == 2 && c.get("notfound").asInt() == 1 && c.get("missing").asInt() == 1 && c.get("norights").asInt() == 1 && c.get("invalid").asInt() == 1, tag + "removal dry run counts (the row without a remove list is invalid, it does not reuse the previous row's list): " + c);
            check(store.get("C.Ok").content.startsWith("{{sensitivity"), tag + "the removal dry run changed no content");
            check(store.get("C.NoObj").content.startsWith("{{sensitivity"), tag + "a page with no stored values reports differs, not removable");
            r = run(migrate, nullAllowed, true, "POST", p("op", "clean", "form_token", "GOOD", "mode", "apply", "rows", j(crows)), st);
            c = r.get("counts");
            check(c.get("applied").asInt() == 2, tag + "removal applied to 2 pages: " + c);
            check(store.get("C.Ok").content.equals("Body"), tag + "C.Ok lost only the macro and its line break: " + store.get("C.Ok").content);
            check(store.get("C.Twice").content.equals("Mid\nEnd"), tag + "an identical macro that appears twice is removed from both places");
            check(store.get("C.Differs").content.startsWith("{{sensitivity") && store.get("C.NotFound").content.equals("Body without the macro") && store.get("C.NoObj").content.startsWith("{{sensitivity") && store.get("C.NoRights").content.startsWith("{{sensitivity"), tag + "skipped pages keep their content");
            check(store.get("C.Next").content.startsWith("{{sensitivity"), tag + "the page whose row had no remove list was not edited");
            check(store.get("C.Ok").saves.size() == 1 && store.get("C.Ok").saves.get(0).contains("Removed"), tag + "one save with a comment per changed page");

            // restore handler: pages without an object must not inherit the previous page's object
            store = new LinkedHashMap<>();
            store.put("R.Has", doc("x", "Management", "Open", "UK", true));
            store.put("R.Blank", doc("x", null, null, null, true));
            List<Object> rrows = new ArrayList<>(List.of(row("R.Has", "Management", "Open", "UK"), row("R.Blank", "Management", "Open", "UK")));
            r = run(restore, nullAllowed, true, "POST", p("form_token", "GOOD", "mode", "dryrun", "rows", j(rrows)), st);
            c = r.get("counts");
            check(c.get("same").asInt() == 1 && c.get("restore").asInt() == 1, tag + "restore dry run: the blank page after a page with values is seen as blank: " + c);

            // save handler and restore from history
            store = new LinkedHashMap<>();
            store.put("SensitivityBanner.Code.Admin", doc("cfg", null, null, null, true));
            Doc cur = doc("page", null, null, null, true); cur.name = "Space.Hist";
            Doc r3 = doc("page", "", "", "", true);
            Doc r2 = doc("page", null, null, null, true);
            Doc r1 = doc("page", "Management", "Open", "", true);
            cur.revs = new ArrayList<>(List.of("1.3", "1.2", "1.1"));
            cur.revDocs.put("1.3", r3); cur.revDocs.put("1.2", r2); cur.revDocs.put("1.1", r1);
            store.put("Space.Hist", cur);
            runSave(common, save, nullAllowed, cur, p("target", "Space.Hist", "form_token", "GOOD", "restoreHistory", "1"), st);
            check(st[0] == 302 && cur.obj != null && "Management".equals(cur.obj.v.get("owner")) && "Open".equals(cur.obj.v.get("security")) && "".equals(cur.obj.v.get("country")), tag + "restore from history finds the newest earlier version that had values, skipping empty and object-less versions");
            check(cur.saves.size() == 1 && cur.saves.get(0).contains("1.1") && lastRedirect.endsWith("/view"), tag + "restore saves once, names the version in the comment and redirects to the page");
            int savesBefore = cur.saves.size();
            runSave(common, save, nullAllowed, cur, p("target", "Space.Hist", "form_token", "GOOD", "restoreHistory", "1"), st);
            check(st[0] == 409 && cur.saves.size() == savesBefore, tag + "restore refuses a page that already has values");
            Doc none = doc("page", null, null, null, true); none.name = "Space.NoHist"; none.revs = new ArrayList<>(List.of("1.2", "1.1")); none.revDocs.put("1.2", doc("p", null, null, null, true)); none.revDocs.put("1.1", doc("p", null, null, null, true));
            store.put("Space.NoHist", none);
            runSave(common, save, nullAllowed, none, p("target", "Space.NoHist", "form_token", "GOOD", "restoreHistory", "1"), st);
            check(st[0] == 404 && none.obj == null && none.saves.isEmpty(), tag + "restore with no earlier values changes nothing");
            Doc forged = doc("page", null, null, null, true); forged.name = "Space.Forged"; forged.revs = new ArrayList<>(List.of("1.1")); forged.revDocs.put("1.1", doc("p", "Management", "", "", true));
            store.put("Space.Forged", forged);
            runSave(common, save, nullAllowed, forged, p("target", "Space.Forged", "form_token", "GOOD", "restoreHistory", "1", "owner", "Standard Staff", "security", "Sensitive"), st);
            check("Management".equals(forged.obj.v.get("owner")) && "".equals(forged.obj.v.get("security")), tag + "values posted with a restore are ignored, only history is used");

            Doc keep = doc("page", "Management", "Open", "UK", true); keep.name = "Space.Keep"; store.put("Space.Keep", keep);
            runSave(common, save, nullAllowed, keep, p("target", "Space.Keep", "form_token", "GOOD", "owner", "Standard Staff"), st);
            check(st[0] == 302 && "Standard Staff".equals(keep.obj.v.get("owner")) && "Open".equals(keep.obj.v.get("security")) && "UK".equals(keep.obj.v.get("country")), tag + "a save that posts one field changes only that field, the others are not cleared");
            runSave(common, save, nullAllowed, keep, p("target", "Space.Keep", "form_token", "GOOD", "owner", "Interns"), st);
            check(st[0] == 400 && "Standard Staff".equals(keep.obj.v.get("owner")), tag + "a value that is not in the configured lists is rejected");
            keep.obj.v.put("country", "Retired Land");
            runSave(common, save, nullAllowed, keep, p("target", "Space.Keep", "form_token", "GOOD", "country", "Retired Land", "security", "Sensitive"), st);
            check(st[0] == 302 && "Retired Land".equals(keep.obj.v.get("country")) && "Sensitive".equals(keep.obj.v.get("security")), tag + "an existing retired value may be kept while another field changes");
            runSave(common, save, nullAllowed, keep, p("target", "Space.Keep", "form_token", "GOOD"), st);
            check(st[0] == 400 && "Retired Land".equals(keep.obj.v.get("country")), tag + "a save with no fields posted changes nothing");
            runSave(common, save, nullAllowed, keep, p("target", "Space.Keep", "form_token", "GOOD", "owner", ""), st);
            check(st[0] == 302 && "".equals(keep.obj.v.get("owner")) && "Sensitive".equals(keep.obj.v.get("security")), tag + "clearing a field works only when that field is posted empty");
            keep.canEdit = false;
            runSave(common, save, nullAllowed, keep, p("target", "Space.Keep", "form_token", "GOOD", "owner", "Open"), st);
            check(st[0] == 403, tag + "no edit right means no save");
            keep.canEdit = true;
            runSave(common, save, nullAllowed, keep, p("target", "Space.Keep", "form_token", "WRONG", "owner", "Management"), st);
            check(st[0] == 403 && "".equals(keep.obj.v.get("owner")), tag + "a bad token means no save");

            // backup handler
            store = new LinkedHashMap<>();
            store.put("B.One", doc("x", "Management", "Open", "UK", true));
            store.put("B.Two", doc("x", "Standard Staff", "Sensitive", "", true));
            // the backup query is a real XWQL statement the stub cannot run, so only check the handler refuses non admins
            r = run(backup, nullAllowed, false, "GET", p("offset", "0", "limit", "500"), st);
            check(st[0] == 403, tag + "backup is refused for non admins");
        }
        System.out.println(failures == 0 ? "\nALL PASSED" : "\n" + failures + " FAILURE(S)");
        System.exit(failures == 0 ? 0 : 1);
    }
}
