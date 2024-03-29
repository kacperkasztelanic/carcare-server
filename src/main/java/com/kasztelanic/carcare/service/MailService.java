package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.domain.Vehicle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import tech.jhipster.config.JHipsterProperties;

import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Service for sending emails.
 * <p>
 * We use the @Async annotation to send emails asynchronously.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final String USER = "user";
    private static final String BASE_URL = "baseUrl";

    private final JHipsterProperties jHipsterProperties;
    private final JavaMailSender javaMailSender;
    private final MessageSource messageSource;
    private final SpringTemplateEngine templateEngine;

    @Async
    public void sendEmail(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        log.debug("Send email[multipart '{}' and html '{}'] to '{}' with subject '{}' and content={}", isMultipart,
            isHtml, to, subject, content);

        // Prepare message using a Spring helper
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, StandardCharsets.UTF_8.name());
            message.setTo(to);
            message.setFrom(jHipsterProperties.getMail().getFrom());
            message.setSubject(subject);
            message.setText(content, isHtml);
            javaMailSender.send(mimeMessage);
            log.debug("Sent email to User '{}'", to);
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.warn("Email could not be sent to user '{}'", to, e);
            } else {
                log.warn("Email could not be sent to user '{}': {}", to, e.getMessage());
            }
        }
    }

    @Async
    public void sendEmailFromTemplate(User user, String templateName, String titleKey) {
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(BASE_URL, jHipsterProperties.getMail().getBaseUrl());
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, locale);
        sendEmail(user.getEmail(), subject, content, false, true);

    }

    @Async
    public void sendEmailFromTemplate(String templateName, String titleKey, User user, Context context) {
        Locale locale = Locale.forLanguageTag(user.getLangKey());
        String content = templateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, locale);
        sendEmail(user.getEmail(), subject, content, false, true);
    }

    @Async
    public void sendInsuranceReminderEmail(User user, Vehicle vehicle, Insurance insurance, int diff) {
        Context context = new Context(Locale.forLanguageTag(user.getLangKey()));
        context.setVariable("user", user);
        context.setVariable("vehicle", vehicle);
        context.setVariable("insurance", insurance);
        context.setVariable("diff", diff);
        sendEmailFromTemplate("mail/insuranceReminderEmail", "email.insurance.title", user, context);
    }

    @Async
    public void sendInspectionReminderEmail(User user, Vehicle vehicle, Inspection inspection, int diff) {
        Context context = new Context(Locale.forLanguageTag(user.getLangKey()));
        context.setVariable("user", user);
        context.setVariable("vehicle", vehicle);
        context.setVariable("inspection", inspection);
        context.setVariable("diff", diff);
        sendEmailFromTemplate("mail/inspectionReminderEmail", "email.inspection.title", user, context);
    }

    @Async
    public void sendRoutineServiceReminderEmail(User user, Vehicle vehicle, RoutineService routineService, int diff) {
        Context context = new Context(Locale.forLanguageTag(user.getLangKey()));
        context.setVariable("user", user);
        context.setVariable("vehicle", vehicle);
        context.setVariable("routineService", routineService);
        context.setVariable("diff", diff);
        sendEmailFromTemplate("mail/serviceReminderEmail", "email.service.title", user, context);
    }

    @Async
    public void sendActivationEmail(User user) {
        log.debug("Sending activation email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/activationEmail", "email.activation.title");
    }

    @Async
    public void sendCreationEmail(User user) {
        log.debug("Sending creation email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/creationEmail", "email.activation.title");
    }

    @Async
    public void sendPasswordResetMail(User user) {
        log.debug("Sending password reset email to '{}'", user.getEmail());
        sendEmailFromTemplate(user, "mail/passwordResetEmail", "email.reset.title");
    }
}
