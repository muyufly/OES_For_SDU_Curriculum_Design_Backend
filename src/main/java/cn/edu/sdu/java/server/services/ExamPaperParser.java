package cn.edu.sdu.java.server.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExamPaperParser {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern MD_QUESTION_HEADER = Pattern.compile("^##\\s+Q\\d+\\s+\\[(CHOICE|READ)]\\s+(\\d+)\\s*$");
    private static final Pattern MD_OPTION = Pattern.compile("^([ABCD])\\.\\s*(.+)$");
    private static final DateTimeFormatter OUTPUT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
    );
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    );

    public ParsedExam parse(String fileName, String content, CsvMeta csvMeta) {
        content = stripBom(content);
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".json")) {
            return parseJson(content);
        }
        if (lowerName.endsWith(".csv")) {
            return parseCsv(content, csvMeta);
        }
        if (lowerName.endsWith(".md") || lowerName.endsWith(".markdown")) {
            return parseMarkdown(content);
        }
        throw new IllegalArgumentException("仅支持 csv、markdown、json 格式");
    }

    private String stripBom(String content) {
        if (content != null && !content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }

    private ParsedExam parseJson(String content) {
        try {
            JsonNode root = mapper.readTree(content);
            ParsedExam exam = new ParsedExam();
            exam.title = text(root, "title");
            exam.courseId = integer(root, "courseId");
            exam.startTime = text(root, "startTime");
            exam.endTime = text(root, "endTime");
            exam.status = normalizeStatus(text(root, "status"));
            JsonNode questions = root.get("questions");
            if (questions == null || !questions.isArray()) {
                throw new IllegalArgumentException("JSON 缺少 questions 数组");
            }
            for (JsonNode node : questions) {
                ParsedQuestion question = new ParsedQuestion();
                question.questionType = normalizeType(text(node, "type", text(node, "questionType")));
                question.content = text(node, "content");
                question.optionA = text(node, "optionA");
                question.optionB = text(node, "optionB");
                question.optionC = text(node, "optionC");
                question.optionD = text(node, "optionD");
                question.answer = text(node, "answer");
                question.score = integer(node, "score");
                exam.questions.add(question);
            }
            validate(exam);
            return exam;
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON 试卷格式错误：" + e.getMessage(), e);
        }
    }

    private ParsedExam parseCsv(String content, CsvMeta meta) {
        if (meta == null) {
            throw new IllegalArgumentException("CSV 上传缺少试卷元信息");
        }
        ParsedExam exam = new ParsedExam();
        exam.title = meta.title;
        exam.courseId = meta.courseId;
        exam.startTime = meta.startTime;
        exam.endTime = meta.endTime;
        exam.status = normalizeStatus(meta.status);

        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        if (lines.length < 2) {
            throw new IllegalArgumentException("CSV 至少需要表头和一行题目");
        }
        String header = String.join(",", parseCsvLine(lines[0])).trim();
        if (!"type,content,optionA,optionB,optionC,optionD,answer,score".equals(header)) {
            throw new IllegalArgumentException("CSV 表头必须为 type,content,optionA,optionB,optionC,optionD,answer,score");
        }
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            List<String> cols = parseCsvLine(lines[i]);
            if (cols.size() != 8) {
                throw new IllegalArgumentException("CSV 第 " + (i + 1) + " 行字段数量不正确");
            }
            ParsedQuestion question = new ParsedQuestion();
            question.questionType = normalizeType(cols.get(0));
            question.content = cols.get(1).trim();
            question.optionA = cols.get(2).trim();
            question.optionB = cols.get(3).trim();
            question.optionC = cols.get(4).trim();
            question.optionD = cols.get(5).trim();
            question.answer = cols.get(6).trim();
            question.score = parsePositiveInt(cols.get(7), "CSV 第 " + (i + 1) + " 行 score");
            exam.questions.add(question);
        }
        validate(exam);
        return exam;
    }

    private ParsedExam parseMarkdown(String content) {
        ParsedExam exam = new ParsedExam();
        ParsedQuestion current = null;
        StringBuilder questionText = new StringBuilder();
        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("# ") && exam.title == null) {
                exam.title = line.substring(2).trim();
                continue;
            }
            if (line.startsWith("courseId:")) {
                exam.courseId = parsePositiveInt(line.substring("courseId:".length()).trim(), "courseId");
                continue;
            }
            if (line.startsWith("startTime:")) {
                exam.startTime = line.substring("startTime:".length()).trim();
                continue;
            }
            if (line.startsWith("endTime:")) {
                exam.endTime = line.substring("endTime:".length()).trim();
                continue;
            }
            if (line.startsWith("status:")) {
                exam.status = normalizeStatus(line.substring("status:".length()).trim());
                continue;
            }
            Matcher header = MD_QUESTION_HEADER.matcher(line);
            if (header.matches()) {
                flushQuestionContent(current, questionText);
                current = new ParsedQuestion();
                current.questionType = header.group(1);
                current.score = parsePositiveInt(header.group(2), "题目分值");
                exam.questions.add(current);
                questionText = new StringBuilder();
                continue;
            }
            if (current == null) {
                throw new IllegalArgumentException("Markdown 题目内容必须位于 ## Qn [TYPE] score 之后");
            }
            Matcher option = MD_OPTION.matcher(line);
            if (option.matches()) {
                flushQuestionContent(current, questionText);
                setOption(current, option.group(1), option.group(2).trim());
                continue;
            }
            if (line.startsWith("Answer:")) {
                flushQuestionContent(current, questionText);
                current.answer = line.substring("Answer:".length()).trim();
                continue;
            }
            if (!questionText.isEmpty()) {
                questionText.append('\n');
            }
            questionText.append(line);
        }
        flushQuestionContent(current, questionText);
        validate(exam);
        return exam;
    }

    private void validate(ParsedExam exam) {
        requireText(exam.title, "title");
        if (exam.courseId == null || exam.courseId <= 0) {
            throw new IllegalArgumentException("courseId 必须为正整数");
        }
        exam.startTime = normalizeStartTime(exam.startTime);
        exam.endTime = normalizeEndTime(exam.endTime);
        validateTimeRange(exam.startTime, exam.endTime);
        exam.status = normalizeStatus(exam.status);
        if (exam.questions.isEmpty()) {
            throw new IllegalArgumentException("试卷至少需要一道题目");
        }
        for (int i = 0; i < exam.questions.size(); i++) {
            ParsedQuestion question = exam.questions.get(i);
            String prefix = "第 " + (i + 1) + " 题";
            question.questionType = normalizeType(question.questionType);
            requireText(question.content, prefix + " content");
            if (question.score == null || question.score <= 0) {
                throw new IllegalArgumentException(prefix + " score 必须为正整数");
            }
            if ("CHOICE".equals(question.questionType)) {
                requireText(question.optionA, prefix + " optionA");
                requireText(question.optionB, prefix + " optionB");
                requireText(question.optionC, prefix + " optionC");
                requireText(question.optionD, prefix + " optionD");
                requireText(question.answer, prefix + " answer");
                if (!question.answer.matches("(?i)[ABCD]")) {
                    throw new IllegalArgumentException(prefix + " CHOICE 答案必须为 A/B/C/D");
                }
                question.answer = question.answer.toUpperCase(Locale.ROOT);
            }
        }
    }

    private static void flushQuestionContent(ParsedQuestion question, StringBuilder text) {
        if (question != null && question.content == null && !text.isEmpty()) {
            question.content = text.toString().trim();
            text.setLength(0);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
    }

    public static String normalizeStartTime(String value) {
        LocalDateTime parsed = parseExamTime(value, false);
        if (parsed == null) {
            throw new IllegalArgumentException("startTime 必须是有效时间，例如 yyyy-MM-dd HH:mm:ss");
        }
        return parsed.format(OUTPUT_TIME_FORMATTER);
    }

    public static String normalizeEndTime(String value) {
        LocalDateTime parsed = parseExamTime(value, true);
        if (parsed == null) {
            throw new IllegalArgumentException("endTime 必须是有效时间，例如 yyyy-MM-dd HH:mm:ss");
        }
        return parsed.format(OUTPUT_TIME_FORMATTER);
    }

    public static void validateTimeRange(String startTime, String endTime) {
        LocalDateTime start = parseExamTime(startTime, false);
        LocalDateTime end = parseExamTime(endTime, true);
        if (start == null || end == null) {
            throw new IllegalArgumentException("startTime/endTime 时间格式无效");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("endTime 必须晚于 startTime");
        }
    }

    public static LocalDateTime parseExamTime(String value) {
        return parseExamTime(value, false);
    }

    private static LocalDateTime parseExamTime(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (Exception ignored) {
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(normalized, formatter);
                return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static String normalizeType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!"CHOICE".equals(normalized) && !"READ".equals(normalized)) {
            throw new IllegalArgumentException("题目类型必须为 CHOICE 或 READ");
        }
        return normalized;
    }

    private static String normalizeStatus(String value) {
        String normalized = value == null || value.isBlank() ? "DRAFT" : value.trim().toUpperCase(Locale.ROOT);
        if (!"DRAFT".equals(normalized) && !"OPEN".equals(normalized) && !"CLOSED".equals(normalized)) {
            throw new IllegalArgumentException("status 必须为 DRAFT、OPEN 或 CLOSED");
        }
        return normalized;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String text(JsonNode node, String field, String fallback) {
        String value = text(node, field);
        return value == null ? fallback : value;
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isInt()) {
            return value.asInt();
        }
        return parsePositiveInt(value.asText(), field);
    }

    private static Integer parsePositiveInt(String value, String field) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " 必须为整数");
        }
    }

    private static void setOption(ParsedQuestion question, String option, String value) {
        switch (option) {
            case "A" -> question.optionA = value;
            case "B" -> question.optionB = value;
            case "C" -> question.optionC = value;
            case "D" -> question.optionD = value;
            default -> throw new IllegalArgumentException("未知选项：" + option);
        }
    }

    private static List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                result.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        result.add(cell.toString());
        return result;
    }

    public static class CsvMeta {
        public String title;
        public Integer courseId;
        public String startTime;
        public String endTime;
        public String status;
    }

    public static class ParsedExam {
        public String title;
        public Integer courseId;
        public String startTime;
        public String endTime;
        public String status;
        public List<ParsedQuestion> questions = new ArrayList<>();
    }

    public static class ParsedQuestion {
        public String questionType;
        public String content;
        public String optionA;
        public String optionB;
        public String optionC;
        public String optionD;
        public String answer;
        public Integer score;
    }
}
