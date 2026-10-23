package com.emerald.backend.audit;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import java.util.*;
@Service public class AuditService{
 private final JdbcTemplate db;public AuditService(JdbcTemplate db){this.db=db;}
 public void record(String actor,String action,String target){db.update("INSERT INTO audit_events(actor,action,target) VALUES (?,?,?)",actor,action,target);}
 public List<Map<String,Object>> list(int page){return db.queryForList("SELECT id,actor,action,target,happened_at FROM audit_events ORDER BY id DESC LIMIT 50 OFFSET ?",page*50L);}
}
