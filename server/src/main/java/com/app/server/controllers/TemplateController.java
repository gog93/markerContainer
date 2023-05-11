package com.app.server.controllers;


import com.app.server.services.service.TemplateService;
import org.apache.coyote.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;

@RestController
@RequestMapping("/template")
public class TemplateController {

    @Autowired
    private TemplateService templateService;


    @GetMapping("/template-list")
    public ResponseEntity<Object> allTemplate(){
        System.out.println("Calleed");
        return ResponseEntity.ok(templateService.findAll());
    }
    @GetMapping("")
    public ResponseEntity<Object> getTemplate(@RequestParam(name = "tag") String tag){
        System.out.println("Calleed");
        return ResponseEntity.ok(templateService.getTemplate(tag));
    }


    @PostMapping("")
    public ResponseEntity<Object> createTemplate(@RequestParam(name = "tag") String tag, @RequestBody HashMap<String, List<String>> data){
        return ResponseEntity.ok(templateService.createTemplate(tag, data));
    }


    @PostMapping("/content")
    public ResponseEntity<Object> sendFilledTemplate(@RequestParam(name = "tag") String tag, @RequestBody List<String> answers){
        System.out.println("The controller is called");
        return ResponseEntity.ok(templateService.getAnswersAndReturnFullTemplate(tag, answers));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteTemplate(@RequestParam(name = "tag") String tag){
        templateService.deleteToken(tag);
        return ResponseEntity.ok("Template for "+tag + ", deleted successfully");
    }

    @PatchMapping("")
    public ResponseEntity<String> updateTemplate(@RequestParam(name = "tag") String tag, @RequestBody HashMap<String, List<String>> data){
        return ResponseEntity.ok(templateService.updateTemplate(tag, data));
    }


}
