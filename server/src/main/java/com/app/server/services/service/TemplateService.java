package com.app.server.services.service;

import com.app.server.entities.Template;

import java.util.HashMap;
import java.util.List;

public interface TemplateService {
    Object getTemplate(String tag);

    String createTemplate(String tag, HashMap<String, List<String>> templateData);

    String getAnswersAndReturnFullTemplate(String tag, List<String> answers);

    String updateTemplate(String tag, HashMap<String, List<String>> data);

    void deleteToken(String tag);
    List<Template> findAll();
}
