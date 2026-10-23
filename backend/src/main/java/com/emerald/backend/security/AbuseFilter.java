package com.emerald.backend.security;
import jakarta.servlet.*;import jakarta.servlet.http.*;import org.springframework.web.filter.OncePerRequestFilter;import java.io.IOException;import java.util.*;
/** Single-instance bounded limiter. Only the trusted edge may reach the API in production. */
public class AbuseFilter extends OncePerRequestFilter {
 private record Window(long start,int count){} private final Map<String,Window>windows=new HashMap<>();
 private synchronized boolean allow(String key,int limit,long now){windows.entrySet().removeIf(e->now-e.getValue().start()>=60000);var w=windows.get(key);if(w==null){if(windows.size()>=10000)return false;windows.put(key,new Window(now,1));return true;}if(w.count()>=limit)return false;windows.put(key,new Window(w.start(),w.count()+1));return true;}
 protected void doFilterInternal(HttpServletRequest q,HttpServletResponse s,FilterChain chain)throws IOException,ServletException{
  String p=q.getServletPath();if(q.getMethod().equals("POST")&&(p.equals("/contact")||p.equals("/auth/login"))){
   String ip=q.getHeader("X-Real-IP");if(ip==null||!ip.matches("[0-9a-fA-F:.]{3,64}"))ip=q.getRemoteAddr();
   if(!allow(p+":"+ip,p.equals("/contact")?5:10,System.currentTimeMillis())){s.setStatus(429);s.setHeader("Retry-After","60");s.setContentType("application/json");s.getWriter().write("{\"message\":\"Too many requests. Wait one minute.\"}");return;}
  }chain.doFilter(q,s);
 }
}
