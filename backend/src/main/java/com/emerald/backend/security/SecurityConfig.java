package com.emerald.backend.security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration public class SecurityConfig {
 @Bean UserDetailsService users(@Value("${emerald.admin.username:admin}") String name,@Value("${emerald.admin.password-hash:}") String hash){
  if(!hash.matches("\\$2[aby]\\$1[0-4]\\$[./A-Za-z0-9]{53}"))throw new IllegalStateException("Set a bcrypt admin password hash with cost 10–14");
  if(!name.matches("[a-zA-Z0-9._-]{1,80}"))throw new IllegalStateException("Invalid administrator username");
  return new InMemoryUserDetailsManager(User.withUsername(name).password(hash).roles("ADMIN").build());
 }
 @Bean BCryptPasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder(12);}
 @Bean SecurityFilterChain chain(HttpSecurity http)throws Exception{
  return http.authorizeHttpRequests(a->a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
   .requestMatchers(HttpMethod.GET,"/content","/auth/csrf","/actuator/health","/media/**").permitAll()
   .requestMatchers(HttpMethod.POST,"/contact").permitAll().requestMatchers("/auth/login").permitAll()
   .requestMatchers("/admin/**","/auth/me").hasRole("ADMIN").anyRequest().denyAll())
   .csrf(c->c.ignoringRequestMatchers("/contact"))
   .formLogin(f->f.loginProcessingUrl("/auth/login").successHandler((q,s,a)->{s.setContentType("application/json");s.getWriter().write("{\"status\":\"signed-in\"}");}).failureHandler((q,s,e)->s.sendError(401)))
   .logout(l->l.logoutUrl("/auth/logout").deleteCookies("JSESSIONID").logoutSuccessHandler((q,s,a)->s.setStatus(204)))
   .exceptionHandling(e->e.authenticationEntryPoint((q,s,x)->s.sendError(401)))
   .sessionManagement(s->s.sessionFixation(f->f.changeSessionId()))
   .headers(h->h.frameOptions(f->f.deny()).contentTypeOptions(org.springframework.security.config.Customizer.withDefaults()))
   .addFilterBefore(new AbuseFilter(),UsernamePasswordAuthenticationFilter.class).build();
 }
}
