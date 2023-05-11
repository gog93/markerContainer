package com.app.server.services;

import com.app.server.entities.Placeholder;
import com.app.server.entities.PlaceholderQuestion;
import com.app.server.entities.Template;
import com.app.server.repositories.PlaceholderQuestionsRepository;
import com.app.server.repositories.PlaceholderRepository;
import com.app.server.repositories.TemplateRepository;
import com.app.server.services.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateServiceImpl implements TemplateService {


    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private PlaceholderRepository placeholderRepository;


    @Autowired
    private PlaceholderQuestionsRepository placeholderQuestionsRepository;

    @Override
    public Object getTemplate(String tag){
        Boolean templateExist = templateRepository.existsByTag(tag);
        System.out.println("Yaaaaah, I was queried");
        if(!templateExist){
            return false;
        }else {
            System.out.println("Token query: Available");
            Template template = templateRepository.findByTag(tag).orElseThrow();
            List<Placeholder> placeholders = placeholderRepository.findAllByTemplateId(template.getId());

            List<String> questionStr = new ArrayList<>();

            HashMap<String, List<String>> templateAndQtn = new HashMap<>();

            for(Placeholder pHolder : placeholders){
                PlaceholderQuestion question = placeholderQuestionsRepository.findByPlaceholderId(pHolder.getId());

                assert question != null;
                    questionStr.add(question.getQuestions());
            }

            templateAndQtn.put(template.getTemplateContent(), questionStr);

            System.out.println("Question Strings: " + questionStr);
            return templateAndQtn;
        }
    }



    @Override
    public String createTemplate(String tag, HashMap<String, List<String>> templateData){

        Boolean templateExists = templateRepository.existsByTag(tag);

        String key = templateData.keySet().iterator().next();
        List<String> value = templateData.values().iterator().next();
        List<String> placeholders = getPlaceholders(key);
        if(!templateExists && value.size() > 1){
            String[] content = key.split("--");
            Template savedTemplate = templateRepository.save(
                    Template.builder()
                            .tag(tag)
                            .createdBy("Medical Hub")
                            .createdAt(LocalDate.now())
                            .templateContent(content[0])
                            .build()
            );

            for (int i = 0; i < placeholders.size(); i++) {
                Placeholder savedPlaceholder = placeholderRepository.save(
                        Placeholder.builder()
                                .placeholder(placeholders.get(i))
                                .templateId(savedTemplate.getId())
                                .build()
                );

                placeholderQuestionsRepository.save(
                        PlaceholderQuestion.builder()
                                .placeholderId(savedPlaceholder.getId())
                                .questions(value.get(i) + "?")
                                .build()
                );
            }
        }else{
            System.out.println("Template already exist");
        }

        return "";
    }


    @Override
    public String getAnswersAndReturnFullTemplate(String tag, List<String> answers){
        Template template = templateRepository.findByTag(tag).orElseThrow();
        String content = template.getTemplateContent();

        Pattern pattern = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");
        Matcher matcher = pattern.matcher(content);

        StringBuffer message = new StringBuffer();
        int i = 0;
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String value = answers.get(i);
            matcher.appendReplacement(message, value);
            i++;
        }
        matcher.appendTail(message);
        return message.toString();
    }



    @Override
    public String updateTemplate(String tag, HashMap<String, List<String>> data){

        Template template = templateRepository.findByTag(tag).orElseThrow();
        String key = data.keySet().iterator().next();
        List<String> values = data.values().iterator().next();
        template.setTemplateContent(key);
        templateRepository.save(template);

        List<Placeholder> placeholders = placeholderRepository.findAllByTemplateId(template.getId());

        int i =0;
        for(Placeholder placeholder: placeholders){
            PlaceholderQuestion qtn = placeholderQuestionsRepository.findByPlaceholderId(placeholder.getId());
            qtn.setQuestions(values.get(i));
            placeholderQuestionsRepository.save(qtn);
            i++;
        }

        return tag + "template Updated successfully";
    }


    @Override
    public void deleteToken(String tag){
        System.out.println("Tag: "+tag);
        Template template = templateRepository.findByTag(tag.trim()).orElseThrow();

        System.out.println("Template: "+template.toString());
        List<Placeholder> placeholders = placeholderRepository.findAllByTemplateId(template.getId());
        System.out.println("Placeholders: "+placeholders);

        for(Placeholder pHolder : placeholders ){
            placeholderQuestionsRepository.delete(placeholderQuestionsRepository.findByPlaceholderId(pHolder.getId()));
            placeholderRepository.deleteById(pHolder.getId());
        }
        templateRepository.delete(template);
    }

    @Override
    public List<Template> findAll() {
        return templateRepository.findAll();
    }


    public static List<String> getPlaceholders(String template){

        List<String> placeholderList = new ArrayList<>();
        try {
                Pattern pattern = Pattern.compile("\\{\\{\\s*(.*?)\\s*\\}\\}");
                Matcher matcher = pattern.matcher(template);

                while (matcher.find()) {
                    String placeholder = matcher.group(1);
                    placeholderList.add(placeholder);
                    System.out.println(placeholder);
                }
        }catch(Exception ex){
            System.out.println(ex.getMessage());
        }
        return placeholderList;
    }
}
