package com.emerald.backend.audit;
import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;import org.springframework.http.HttpStatus;import java.util.*;
@RestController @RequestMapping("/admin/audit") public class AuditController{
 private final AuditService audit;public AuditController(AuditService audit){this.audit=audit;}
 @GetMapping public List<Map<String,Object>> list(@RequestParam(defaultValue="0")int page){if(page<0||page>100000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);return audit.list(page);}
}
