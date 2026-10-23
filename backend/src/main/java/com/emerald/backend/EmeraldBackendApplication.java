package com.emerald.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EmeraldBackendApplication {

    public static void main(String[] args) {
        if(args.length==1&&args[0].equals("--hash-password")){var console=System.console();if(console==null)throw new IllegalStateException("Use an interactive terminal (-it)");char[] secret=console.readPassword("Admin password (16+ characters): ");try{if(secret.length<16)throw new IllegalArgumentException("Use at least 16 characters");System.out.println(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(new String(secret)));}finally{java.util.Arrays.fill(secret,'\0');}return;}
        SpringApplication.run(EmeraldBackendApplication.class,args);
    }
}
