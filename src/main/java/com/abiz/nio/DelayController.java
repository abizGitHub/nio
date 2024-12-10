package com.abiz.nio;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/delay")
public class DelayController {

    @GetMapping("/{millis}")
    public String replyWithDelay(@PathVariable Long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Thread.currentThread().getName() + "\n";
    }

}
