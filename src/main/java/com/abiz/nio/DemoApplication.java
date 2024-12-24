package com.abiz.nio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

@SpringBootApplication
public class DemoApplication {

    static int port;

    public static void main(String[] args) throws IOException {
        if (args.length == 0)
            port = readFromProperties();
        else
            port = Integer.parseInt(args[0]);
        SpringApplication.run(DemoApplication.class, args);
    }

    private static int readFromProperties() throws IOException {
        var properties = new Properties();
        properties.load(new FileInputStream("config.properties"));
        return Integer.valueOf(properties.getProperty("server_port"));
    }

    @Configuration
    class CustomContainer implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {
        public void customize(ConfigurableServletWebServerFactory factory) {
            factory.setPort(port);
        }
    }

}
