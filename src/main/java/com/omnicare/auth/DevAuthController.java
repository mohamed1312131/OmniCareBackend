package com.omnicare.auth;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Comparator;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth/dev")
public class DevAuthController {

    private final RequestMappingHandlerMapping handlerMapping;

    public DevAuthController(RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    public String callback(@RequestParam(value = "token", required = false) String token) {
        String safeToken = token == null ? "" : htmlEscape(token);

        return "<!doctype html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\" />\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />\n"
                + "  <title>Omnicare Dev Auth Callback</title>\n"
                + "  <style>body{font-family:system-ui,Segoe UI,Arial;margin:24px}textarea{width:100%;height:220px}code{background:#f3f3f3;padding:2px 4px;border-radius:4px}</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <h1>OAuth2 login success</h1>\n"
                + (token == null
                ? "  <p><b>No token</b> was provided on the query string.</p>\n"
                : "  <p>Copy the JWT below and use it as <code>Authorization: Bearer &lt;token&gt;</code>.</p>\n")
                + "  <textarea readonly>" + safeToken + "</textarea>\n"
                + "  <h2>Example</h2>\n"
                + "  <pre>curl -H \"Authorization: Bearer " + safeToken + "\" http://localhost:8080/api/account/me</pre>\n"
                + "</body>\n"
                + "</html>\n";
    }

    @GetMapping(value = "/mappings", produces = MediaType.TEXT_PLAIN_VALUE)
    @ResponseBody
    public String mappings() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .map(e -> e.getKey() + " -> " + e.getValue().getBeanType().getSimpleName() + "#" + e.getValue().getMethod().getName())
                .collect(Collectors.joining("\n"));
    }

    private static String htmlEscape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
