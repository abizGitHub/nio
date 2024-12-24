package com.abiz.nio;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/delay")
public class DelayController {

    long startMillis = System.currentTimeMillis();

    @GetMapping("/{millis}")
    public String replyWithDelay(@PathVariable Long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        var response = String.format(
                "%-14s %-6s %-10d",
                Thread.currentThread().getName(),
                millis,
                (System.currentTimeMillis() - startMillis));
        System.out.println(response);
        return response + "\n";
    }

}
