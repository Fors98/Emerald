package com.emerald.backend.content;
import com.emerald.backend.audit.AuditService;import jakarta.validation.Valid;import jakarta.validation.constraints.*;
import org.springframework.core.io.ClassPathResource;import org.springframework.http.HttpStatus;import org.springframework.transaction.annotation.Transactional;import org.springframework.web.bind.annotation.*;import org.springframework.web.server.ResponseStatusException;
import java.util.*;import java.security.Principal;import java.io.*;import java.nio.charset.StandardCharsets;
@RestController @RequestMapping("/admin/content") public class AdminContentController{
 private final SiteContentRepository repository;private final AuditService audit;private final Set<String>keys;
 public AdminContentController(SiteContentRepository repository,AuditService audit)throws IOException{this.repository=repository;this.audit=audit;try(var in=new ClassPathResource("content-keys.txt").getInputStream()){keys=parseKeys(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
 static Set<String> parseKeys(String text) { return new HashSet<>(text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList()); }
 public record ContentUpdate(@NotBlank @Size(max=20000)String value,@NotNull Long version){}
 @GetMapping public Map<String,Object> list(){Map<String,Object>out=new TreeMap<>();repository.findAll().forEach(i->out.put(i.getKey(),Map.of("value",i.getValue(),"version",i.getVersion())));return out;}
 @PutMapping("/{key}") @Transactional public Map<String,Object> update(@PathVariable String key,@Valid @RequestBody ContentUpdate request,Principal p){
  if(!keys.contains(key))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  SiteContent item=repository.findById(key).orElse(null);
  if((item==null&&request.version()!=-1L)||(item!=null&&!item.getVersion().equals(request.version())))throw new ResponseStatusException(HttpStatus.CONFLICT);
  if(key.startsWith("media.")&&!request.value().matches("/api/media/[a-f0-9-]{36}\\.(png|jpg)"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  if(item==null)item=new SiteContent(key,request.value());else item.updateValue(request.value());repository.saveAndFlush(item);audit.record(p.getName(),"CONTENT_SAVED",key);return Map.of("key",key,"value",item.getValue(),"version",item.getVersion());
 }
 @DeleteMapping("/{key}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional public void delete(@PathVariable String key,@RequestParam Long version,Principal p){
  if(!keys.contains(key))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);var item=repository.findById(key).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));if(!item.getVersion().equals(version))throw new ResponseStatusException(HttpStatus.CONFLICT);repository.delete(item);repository.flush();audit.record(p.getName(),"CONTENT_RESET",key);
 }
}
