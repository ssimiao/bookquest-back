package io.bookquest.entrypoint.v1;

import io.bookquest.usecase.BotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class BotController {

    @Autowired
    private BotService botService;

    @GetMapping("/users/{username}/bot-options")
    public ResponseEntity<Object> getAllClassesOptions(@PathVariable("username") String username) {
        botService.getAllAvaiableClass(username);
        return ResponseEntity.ok().build();
    }
}
