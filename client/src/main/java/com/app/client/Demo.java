package com.app.client;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;


@Slf4j
@EnableScheduling
@Service
public class Demo {

    @Value("${folder.path}")
    private String folderPath;


    @Value("${base.url}")
    private String baseUrl;


    @Scheduled(fixedRate = 1000)
    public void run() {
        String helper = "@Helper";
        HashMap<String, List<String>> queryData = new HashMap<>();
        StringBuilder tagEmail = new StringBuilder();
        StringBuilder tagH = new StringBuilder();
        StringBuilder editTag = new StringBuilder();
        List<File> files = getFilesModifiedAfterLastSchedule(new File(folderPath));
        for (File file : files) {
            String fileName = file.getAbsolutePath();
            List<String> lines = linesToArrayList(fileName);
            StringBuilder queryMatch = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.equals(helper)) {
                        String nextLine = br.readLine();
                        if (nextLine == null) {
                            try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName, true))) {
                                bw.write("-");
                            } catch (IOException e) {
                                System.err.format("IOException: %s%n", e);
                            }
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                System.err.format("IOException: %s%n", e);
            }
            getTemplateTag(fileName);
            String lastLine = getLastNonEmptyLineOfFile(fileName);
            Pattern pattern = Pattern.compile("^///([a-z]+)\\.$");
            Pattern pattern2 = Pattern.compile("@(\\w+-)?(\\w+)\\.");
            for(String line : lines) {
                Matcher matcher = pattern.matcher(line);
                Matcher matcher2 = pattern2.matcher(line);
                if (matcher.matches()) {
                    tagEmail.delete(0, tagEmail.length());
                    String matchedWord = matcher.group(1);
                    tagEmail.append(matchedWord);
                    queryMatch.append(line);
                }else if (matcher2.matches()) {
                    editTag.delete(0, editTag.length());
                    String email = matcher2.group(2);
                    tagH.append(email);
                    editTag.append(email);
                }
            }
            if(!lines.isEmpty() && lines.get(0).startsWith(helper)) {
                startTemplateCreation(fileName, tagEmail.toString());
            }
            String[] templateContentArr = getTemplateContent(fileName).split("-");
            String templateContent = templateContentArr[0];
            getPlaceholders(fileName, templateContent);
            String lastPlaceholder = getLastPlaceholder(templateContent, fileName);
            List<String> questions = getPlaceholderQuestions(fileName, lastPlaceholder);
            queryData.put(templateContent, questions);
            List<String> value = queryData.values().iterator().next();
            if(value.size() != 0 && lastLine.startsWith("@End") && lines.get(0).startsWith(helper)) {
                postQueryApi(tagH.toString(), queryData, baseUrl);
                clearFile(fileName);
            }
            getTokenQuestions(fileName, tagEmail.toString());
            displayQuestion(fileName, tagEmail.toString(), queryMatch.toString());
            if(lastLine.startsWith("@Delete-")){
                handleDeleteToken(fileName);
            }
            if(lastLine.startsWith("@Edit-")){
                handleEditToken(fileName);
            }
            if(!lines.isEmpty() && lines.get(0).startsWith("@Edit-") && lines.get(lines.size()-1).startsWith("@End")){
                saveEdit(fileName, editTag.toString());
                clearFile(fileName);
            }
        }
    }

    protected List<File> getFilesModifiedAfterLastSchedule(File directory) {
        List<File> filesModified = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        long fiveMinutesAgo = currentTime - (60 * 1000);

        File[] fileList = directory.listFiles();
        if (fileList != null) {
            for (File file : fileList) {
                if (file.isDirectory()) {
                    // recursively call this method for subdirectories
                    filesModified.addAll(getFilesModifiedAfterLastSchedule(file));
                } else {
                    try {
                        Path filePath = file.toPath();
                        BasicFileAttributes attributes = Files.readAttributes(filePath, BasicFileAttributes.class);
                        FileTime lastModifiedTime = attributes.lastModifiedTime();

                        if (lastModifiedTime.toMillis() >= fiveMinutesAgo) {
                            filesModified.add(file);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return filesModified;
    }



    public void displayQuestion(String fileName, String tagEmail, String queryMatch) {
        List<String> questions = getTokenQuestions(fileName, tagEmail);
        List<String> lines = linesToArrayList(fileName);
        StringBuilder tag = new StringBuilder();
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName, true))) {
            boolean shouldContinue = false;

            Pattern pattern = Pattern.compile("^///([a-z]+)\\.$");
            if (!lines.isEmpty()) {
                Matcher matcher = pattern.matcher(lines.get(lines.size() - 1));
                if (matcher.matches()) {
                    String matchedWord = matcher.group(1);
                    tag.append(matchedWord);
                }
            }

            if (!questions.isEmpty()){
                for (String line : lines) {
                    if (line.equalsIgnoreCase("///" + tag.toString() + ".")) {
                        writer.append("\n").append(questions.get(0));
                        writer.flush();
                        shouldContinue = true;
                        break;
                    }
                }
            }


            if (shouldContinue) {
                for (int i = 1; i < questions.size();) {
                    shouldContinue = isLastLineAnswered(fileName);
                    while (!shouldContinue) {
                        shouldContinue = isLastLineAnswered(fileName);
                        Thread.sleep(1000); // Wait for 1 second before checking again
                    }
                    writer.append("\n").append(questions.get(i));
                    writer.flush();
                    i++;
                }
            } else {
                return;
            }
            String filledTemplate = getAnswers(fileName, tag.toString(), queryMatch);
            assert filledTemplate != null;

            String[] words = filledTemplate.split(" ");
            StringBuilder sb = new StringBuilder();
            String lineSeparator = System.getProperty("line.separator");
            writer.append(lineSeparator);
            for (String word : words) {
                if (sb.length() + word.length() > 80) {
                    writer.append(sb.toString().trim()).append(lineSeparator);
                    sb = new StringBuilder();
                }
                sb.append(word).append(" ");
            }
            if (sb.length() > 0) {
                writer.append(sb.toString().trim()).append(lineSeparator);
            }
            writer.flush();
            deleteEmailSection(fileName);
        } catch (IOException ex) {
            log.info("Error while writing to the file");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public void deleteEmailSection(String fileName) {
        List<String> lines = linesToArrayList(fileName);

        int startIndex = -1;
        int endIndex = -1;

        Pattern emailPattern = Pattern.compile("^///[a-zA-Z]+\\.$"); // regular expression to match lines that start with "///" followed by one or more alphabetical characters, followed by a period

        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = emailPattern.matcher(lines.get(i));
            if (matcher.matches()) {
                startIndex = i;
            } else if (lines.get(i).contains("///End")) {
                endIndex = i;
                break;
            }
        }

        if (startIndex != -1 && endIndex != -1) {
            String separator = System.lineSeparator();
            StringBuilder sb = new StringBuilder();
            sb.append(separator);
            lines.subList(startIndex, endIndex + 1).clear();
            lines.add(startIndex, sb.toString());
            writeArrayListToFile(lines, fileName);
        }
    }




    public void writeArrayListToFile(List<String> lines, String fileName) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(fileName))) {
            for (String line : lines) {
                writer.println(line);
            }
            writer.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }




    public String getAnswers(String fileName, String tag, String queryMatch){
        List<String> lines = linesToArrayList(fileName);

        for(Integer j = 0; j< lines.size(); j++) {
            if (!lines.isEmpty() && lines.get(lines.size() - 1).equals("///End")) {
                // read lines in reverse order until "--Template" is found
                ArrayList<String> reversedLines = new ArrayList<String>();
                for (int i = lines.size() - 2; i >= 0; i--) {
                    if (lines.get(i).equals(queryMatch)) {
                        break;
                    } else if (lines.get(i).equals("///" + tag + ".")) {
                        break;
                    } else {
                        reversedLines.add(lines.get(i));
                    }
                }

                // reverse the order of lines and print them out
                 Collections.reverse(reversedLines);
                List<String> answers = new ArrayList<>();
                for(String line : reversedLines){
                    String[] wordList = line.split("\\?");
                    String[] answer = wordList[1].split("/");
                    answers.add(answer[0]);
                }

                return postQueryApi(tag, answers, baseUrl + "/content");

            }
        }

         return null;
    }


    public static boolean isLastLineAnswered(String filePath) {
        List<String> lines = linesToArrayList(filePath);

        return lines.get(lines.size() -1).endsWith("///");
    }


    public void handleDeleteToken(String fileName){
        List<String> lines = linesToArrayList(fileName);

        try(PrintWriter writer = new PrintWriter(new FileWriter(fileName, true))) {

            for (String line : lines) {
                if (line.startsWith("@Delete")) {
                    String[] parts = line.split("-");
                    String tag = parts[1].replace(".", "");

//                    writer.append("\n").append("Are you sure(Y/N)?");
//                    String[] gettingAnswer = lines.get(lines.size() -1).split("\\?");
//                    String answer = gettingAnswer[1];

//                    if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {
                        String result = deleteTemplate(tag, baseUrl + "/delete");
                        writer.append(result);
//                    }else{
//                         writer.append("\n").append("Delete operation stopped");
//                    }
                }
            }
        }catch (Exception ex){
            log.info("Error writing to file");
        }
    }



    public String handleEditToken(String fileName){
        List<String> lines = linesToArrayList(fileName);

        Pattern pattern = Pattern.compile("@(\\w+-)?(\\w+)\\.");
        StringBuilder tag = new StringBuilder();
        if(lines.get(lines.size() - 1).startsWith("@Edit")){
            Matcher matcher = pattern.matcher(lines.get(lines.size() - 1));

            if (matcher.matches()){
                String token = matcher.group(2);
                tag.append(token);
            }

            String template = getQueryApi(tag.toString());

            ObjectMapper mapper = new ObjectMapper();

            Map<String, List<String>> json = null;
            try {
                json = mapper.readValue(template, Map.class);
            } catch (JsonProcessingException e) {
               log.info("Token not found");
            }
            try(PrintWriter writer = new PrintWriter(new FileWriter(fileName, true))){
                // Extract the key and values from the JSON object
                Iterator<Map.Entry<String, List<String>>> iterator = json.entrySet().iterator();
                String key = null;
                List<String> value = new ArrayList<>();
                while (iterator.hasNext()) {
                    Map.Entry<String, List<String>> entry = iterator.next();
                     key= entry.getKey();
                     value.addAll(entry.getValue());
                }
                StringBuilder str = new StringBuilder();
                String[] words = key.split(" ");
                String lineSeparator = System.getProperty("line.separator");


                if(!json.isEmpty()){
                    writer.append(lineSeparator);
                    writer.append("--Template");
                    writer.append(lineSeparator);
                    for (String word : words) {
                        if (str.length() + word.length() > 80) {
                            writer.append(str.toString().trim()).append(lineSeparator);
                            str = new StringBuilder();
                        }
                        str.append(word).append(" ");
                    }
                    if (str.length() > 0) {
                        writer.append(str.toString().trim()).append(lineSeparator);
                    }
                    writer.append("--wizard");
                    assert value != null;
                    List<String> questions = value;
                    for(String qtn : questions){
                        writer.append(lineSeparator);
                        writer.append(qtn);
                    }
                }

            }catch (Exception ex){
                log.info("An error occurred while writing to file");
            }

        }
        return tag.toString();
    }


    public void saveEdit(String fileName, String tag) {
        List<String> lines = linesToArrayList(fileName);

        HashMap<String, List<String>> data = new HashMap<>();

        String newTemplate = extractEditedTemplate(lines);
        List<String> newQuestions = extractEditedQuestions(lines);

        data.put(newTemplate, newQuestions);

        patchQueryApi(tag, data, baseUrl);
    }




    public List<String> getTokenQuestions(String fileName, String tag) {
        List<String> lines = linesToArrayList(fileName);
        boolean foundTag = false;

        for (String line : lines) {
            if (line.equalsIgnoreCase("///" + tag + ".")) {
                foundTag = true;
                System.out.println(tag);
                break;
            }
        }

        if (foundTag) {
            try {
                String arrStrFormat = getQueryApi(tag);
                Gson gson = new Gson();
                JsonObject jsonObject = gson.fromJson(arrStrFormat, JsonObject.class);
                JsonArray jsonArray = null;
                for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        jsonArray = entry.getValue().getAsJsonArray();
                        break;
                    }
                }
                List<String> questionList = new ArrayList<>();
                if (jsonArray != null) {
                    for (JsonElement element : jsonArray) {
                        questionList.add(element.getAsString());
                    }
                }
                questionList.add("///End");
                return questionList;
            } catch (Exception ex) {
                log.info("Json parser error");
            }
        }
        return new ArrayList<>();
    }



    public String getTemplateTag(String filePath) {
        String helper = "@Helper-";
        String lastLine = getLastNonEmptyLineOfFile(filePath);
        StringBuilder tag = new StringBuilder();

        if (lastLine != null && lastLine.startsWith(helper)) {
            int emailIndex = lastLine.indexOf(helper) + helper.length();
            int dotIndex = lastLine.indexOf(".", emailIndex);
            if (dotIndex > emailIndex) {
                String email = lastLine.substring(emailIndex, dotIndex);
                tag.append(email);
                String queryResponse = getQueryApi(email);
                if (queryResponse.equalsIgnoreCase("false")) {
                    try (PrintWriter writer = new PrintWriter(new FileWriter(filePath, true))) {
                        writer.println("\nThere is no existing template with the name "+ email + ", " + "Do you want to create a new template with the name " + email + "(Y/N)?");
                    } catch (IOException e) {
                        System.err.format("IOException: %s%n", e);
                    }
                }
            }
        }
        return tag.toString();
    }



    public static void startTemplateCreation(String filePath, String tag){
        String[] lineArray = getLastNonEmptyLineOfFile(filePath).split("\\?");
        if (lineArray.length > 1) {
            String answer = lineArray[1].trim().toLowerCase();
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {
                try {
                    PrintWriter writer = new PrintWriter(new FileWriter(filePath, true));
                    writer.println("@Helper-"+tag.toLowerCase());
                    writer.println("--Template");
                    writer.println("please write here the template contents, after finishing, go to the next line and type '--wizard'");
                    writer.println("@Helper-"+tag.toLowerCase());
                    writer.println("--Template");
                    writer.close();
                } catch (IOException e) {
                    System.err.format("IOException: %s%n", e);
                }
            }
        }
    }


    public static String getTemplateContent(String fileName) {

        List<String> lines = linesToArrayList(fileName);

            // check if the last non-empty line is "--wizard"
            StringBuilder resultBuilder = new StringBuilder();
            for(Integer j = 0; j< lines.size(); j++) {
                if (!lines.isEmpty() && (lines.get(j).equals("--wizard") || lines.get(j).equals("--Wizard"))) {
                    // read lines in reverse order until "--Template" is found
                    ArrayList<String> reversedLines = new ArrayList<String>();
                    for (int i = lines.size() - 2; i >= 0; i--) {
                        if (lines.get(i).equals("--Template")) {
                            break;
                        } else {
                            reversedLines.add(lines.get(i));
                        }
                    }

                    // reverse the order of lines and print them out
                    Collections.reverse(reversedLines);
                    for (String l : reversedLines) {
                        resultBuilder.append(l);
                    }
                }
            }
            getPlaceholders(fileName, resultBuilder.toString());
            return resultBuilder.toString();
    }



    public static void getPlaceholders(String fileName, String template){

        List<String> placeholderList = new ArrayList<>();
        List<String> lines = linesToArrayList(fileName);

        try{
            if(!lines.isEmpty() && lines.get(lines.size()-1).equalsIgnoreCase("--wizard")) {
                File file = new File(fileName);
                FileWriter fileWriter = new FileWriter(file, true);
                Pattern pattern = Pattern.compile("\\{\\{\\s*(.*?)\\s*\\}\\}");
                Matcher matcher = pattern.matcher(template);

                while (matcher.find()) {
                    String placeholder = matcher.group(1);
                    fileWriter.append("\n").append(placeholder).append("?");
                    placeholderList.add(placeholder);
                }
                fileWriter.append("\n");
                fileWriter.close();
            }else{
                return;
            }

        }catch(Exception ex){
            System.out.println(ex.getMessage());
        }
    }



    public String getLastPlaceholder(String template, String fileName){
        List<String> placeholders = new ArrayList<>();
        List<String> lines = linesToArrayList(fileName);

        Pattern pattern = Pattern.compile("\\{\\{\\s*(.*?)\\s*\\}\\}");

        if(!lines.isEmpty() && template != null) {
            Matcher matcher = pattern.matcher(template);

            while (matcher.find()) {
                String placeholder = matcher.group(1);
                placeholders.add(placeholder);
            }
            if(!placeholders.isEmpty()) {
                return placeholders.get(placeholders.size() - 1).trim();
            }
        }
        return "";
    }



    public static List<String> getPlaceholderQuestions(String fileName, String lastPlaceholder) {

        List<String> lines = linesToArrayList(fileName);

            // check if the last non-empty line is "--wizard"
            StringBuilder resultBuilder = new StringBuilder();
            for(Integer j = 0; j< lines.size(); j++) {
                if (!lines.isEmpty() && lines.get(j).equals("@End")) {
                    // read lines in reverse order until "url?" is found
                    ArrayList<String> reversedLines = new ArrayList<String>();
                    for (int i = lines.size() - 2; i >= 0; i--) {
                        if (lines.get(i).equals(lastPlaceholder+"?")) {
                            break;
                        } else {
                            reversedLines.add(lines.get(i));
                        }
                    }

                    // reverse the order of lines and print them out
                    Collections.reverse(reversedLines);
                    for (String l : reversedLines) {
                        resultBuilder.append(l);
                    }

                }
            }
            String[] questionsArr = resultBuilder.toString().split("\\?");
            return Arrays.asList(questionsArr);
    }



    public static String getLastNonEmptyLineOfFile(String fileName) {
        List<String> lines = linesToArrayList(fileName);
        StringBuilder lastLine = new StringBuilder();
        if(!lines.isEmpty()) {
            lastLine.append(lines.get(lines.size() - 1));
        }
        return lastLine.toString();
    }


    public String getQueryApi(String parameter) {
        try {
            CloseableHttpClient client = HttpClients.createDefault();

            URIBuilder uriBuilder = new URIBuilder(baseUrl);
            uriBuilder.addParameter("tag", parameter);

            String requestURI = uriBuilder.build().toString();

            HttpGet getRequest = new HttpGet(requestURI);

            CloseableHttpResponse response = client.execute(getRequest);

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                HttpEntity httpEntity = response.getEntity();
                if (httpEntity != null) {
                    InputStream inputStream = httpEntity.getContent();
                    String responseString = new BufferedReader(new InputStreamReader(inputStream))
                            .lines()
                            .collect(Collectors.joining("\n"));
                    return responseString;
                }
            }

        } catch (Exception ex) {
            log.info("Connection refused");
        }
        return null;
    }



    public String postQueryApi(String parameter, Object data, String url) {
        try {
            CloseableHttpClient client = HttpClients.createDefault();

            URIBuilder uriBuilder = new URIBuilder(url);
            uriBuilder.addParameter("tag", parameter);

            String requestURI = uriBuilder.build().toString();

            HttpPost postRequest = new HttpPost(requestURI);
            postRequest.setHeader("Content-Type", "application/json");

            ObjectMapper objectMapper = new ObjectMapper();
            String requestBody = null;
            try {
                requestBody = objectMapper.writeValueAsString(data);
            } catch (JsonProcessingException e) {
                // handle exception
            }

            postRequest.setEntity(new StringEntity(requestBody));

            CloseableHttpResponse response = client.execute(postRequest);

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                HttpEntity httpEntity = response.getEntity();
                if (httpEntity != null) {
                    InputStream inputStream = httpEntity.getContent();
                    String responseString = new BufferedReader(new InputStreamReader(inputStream))
                            .lines()
                            .collect(Collectors.joining("\n"));
                    return responseString;
                }
            }

        } catch (Exception ex) {
            log.info("Connection refused");
        }
        return null;
    }


    public String patchQueryApi(String parameter, Object data, String url) {
        try {
            CloseableHttpClient client = HttpClients.createDefault();

            URIBuilder uriBuilder = new URIBuilder(url);
            uriBuilder.addParameter("tag", parameter);

            String requestURI = uriBuilder.build().toString();

            HttpPatch patchRequest = new HttpPatch(requestURI);
            patchRequest.setHeader("Content-Type", "application/json");

            ObjectMapper objectMapper = new ObjectMapper();
            String requestBody = null;
            try {
                requestBody = objectMapper.writeValueAsString(data);
            } catch (JsonProcessingException e) {
                // handle exception
            }

            patchRequest.setEntity(new StringEntity(requestBody));

            CloseableHttpResponse response = client.execute(patchRequest);

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                HttpEntity httpEntity = response.getEntity();
                if (httpEntity != null) {
                    InputStream inputStream = httpEntity.getContent();
                    String responseString = new BufferedReader(new InputStreamReader(inputStream))
                            .lines()
                            .collect(Collectors.joining("\n"));
                    return responseString;
                }
            }

        } catch (Exception ex) {
            log.info("Connection refused");
        }
        return null;
    }


    public String deleteTemplate(String parameter, String url){
        String responseString = null;
        try{
            CloseableHttpClient client = HttpClients.createDefault();

            URIBuilder uriBuilder = new URIBuilder(url);
            uriBuilder.addParameter("tag", parameter);

            String requestURI = uriBuilder.build().toString();
            HttpDelete deleteRequest = new HttpDelete(requestURI);

            CloseableHttpResponse response = client.execute(deleteRequest);

            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                HttpEntity httpEntity = response.getEntity();
                if (httpEntity != null) {
                    InputStream inputStream = httpEntity.getContent();
                    responseString = new BufferedReader(new InputStreamReader(inputStream))
                            .lines()
                            .collect(Collectors.joining("\n"));
                }
            }

        }catch (Exception ex){

        }
        return responseString;
    }

    public static void clearFile(String filePath) {


        List<String> lines = linesToArrayList(filePath);
        try {

            if(!lines.isEmpty()) {

                FileWriter writer = new FileWriter(filePath);
                writer.write("");
                writer.close();
            }
        }catch(Exception ex){
            System.out.println(ex.getMessage());
        }
    }

    private String extractEditedTemplate(List<String> lines){
        StringBuilder template = new StringBuilder();
        boolean inTemplate = false;
        for(String line : lines){
            if(line.startsWith("--Template")){
                inTemplate = true;
            } else if(line.startsWith("--wizard") || line.startsWith("--Wizard")){
                break;
            } else if(inTemplate){
                template.append(line).append(" ");
            }
        }
        return template.toString().trim();
    }

    private List<String> extractEditedQuestions(List<String> lines){
        List<String> questions = new ArrayList<>();
        boolean inQuestions = false;
        for(String line : lines){
            if(line.startsWith("--wizard") || line.startsWith("--Wizard")){
                inQuestions = true;
            } else if(line.startsWith("@End")){
                break;
            } else if(inQuestions){
                String question = line.trim();
                if(!question.isEmpty()){
                    questions.add(question);
                }
            }
        }
        return questions;
    }


    public static List<String> linesToArrayList(String filePath){

        List<String> lines = new ArrayList<>();
        try(BufferedReader br = new BufferedReader(new FileReader(filePath))){

            String line;
            while((line = br.readLine()) != null){
                if(!line.trim().isEmpty()){
                    lines.add(line);
                }
            }

        }catch(Exception ex){

        }
        return lines;
    }





}
