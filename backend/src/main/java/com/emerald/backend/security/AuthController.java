package com.emerald.backend.security;
import org.springframework.security.web.csrf.CsrfToken;import org.springframework.web.bind.annotation.*;import java.security.Principal;import java.util.Map;
@RestController @RequestMapping("/auth") public class AuthController {
 @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken t){return Map.of("token",t.getToken(),"headerName",t.getHeaderName());}
 @GetMapping("/me") public Map<String,String> me(Principal p){return Map.of("username",p.getName());}
}
