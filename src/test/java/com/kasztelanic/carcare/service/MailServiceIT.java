package com.kasztelanic.carcare.service;

import com.kasztelanic.carcare.config.Constants;

import com.kasztelanic.carcare.CarcareApp;
import com.kasztelanic.carcare.config.ApplicationProperties;
import com.kasztelanic.carcare.domain.Inspection;
import com.kasztelanic.carcare.domain.Insurance;
import com.kasztelanic.carcare.domain.RoutineService;
import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.domain.Vehicle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;

import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link MailService}.
 */
@SpringBootTest(classes = CarcareApp.class)
class MailServiceIT {

    private static final String[] LANGUAGES = { "en", "pl" };
    private static final LocalDate REMINDER_DATE = LocalDate.of(2026, 4, 18);
    private static final Pattern PATTERN_LOCALE_3 = Pattern.compile("([a-z]{2})-([a-zA-Z]{4})-([a-z]{2})");
    private static final Pattern PATTERN_LOCALE_2 = Pattern.compile("([a-z]{2})-([a-z]{2})");

    @Autowired
    private ApplicationProperties applicationProperties;
    @Autowired
    private SpringTemplateEngine templateEngine;

    @Spy
    private JavaMailSenderImpl javaMailSender;

    @Captor
    private ArgumentCaptor<MimeMessage> messageCaptor;

    private MailService mailService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
        doNothing().when(javaMailSender).send(any(MimeMessage.class));
        ResourceBundleMessageSource testMessageSource = new ResourceBundleMessageSource();
        testMessageSource.setBasenames("i18n/test-messages", "i18n/messages");
        testMessageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        templateEngine.setTemplateEngineMessageSource(testMessageSource);
        mailService = new MailService(applicationProperties, javaMailSender, testMessageSource, templateEngine);
    }

    @AfterEach
    void restoreTemplateEngineMessageSource() {
        // The engine is a context singleton shared with every other test class in the cached
        // context. Boot's ThymeleafAutoConfiguration never sets templateEngineMessageSource, so
        // null is its pristine state: the engine then falls back to the MessageSourceAware
        // messageSource Spring injected.
        templateEngine.setTemplateEngineMessageSource(null);
    }

    @Test
    void testSendEmail() throws Exception {
        mailService.sendEmail("john.doe@example.com", "testSubject", "testContent", false, false);
        verify(javaMailSender).send(messageCaptor.capture());
        MimeMessage message = messageCaptor.getValue();
        assertThat(message.getSubject()).isEqualTo("testSubject");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("john.doe@example.com");
        assertThat(message.getFrom()[0].toString()).isEqualTo(applicationProperties.getMail().getFrom());
        assertThat(message.getContent()).isInstanceOf(String.class);
        assertThat(message.getContent().toString()).isEqualTo("testContent");
        assertThat(message.getDataHandler().getContentType()).isEqualTo("text/plain; charset=UTF-8");
    }

    @Test
    void testSendHtmlEmail() throws Exception {
        mailService.sendEmail("john.doe@example.com", "testSubject", "testContent", false, true);
        verify(javaMailSender).send(messageCaptor.capture());
        MimeMessage message = messageCaptor.getValue();
        assertThat(message.getSubject()).isEqualTo("testSubject");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("john.doe@example.com");
        assertThat(message.getFrom()[0].toString()).isEqualTo(applicationProperties.getMail().getFrom());
        assertThat(message.getContent()).isInstanceOf(String.class);
        assertThat(message.getContent().toString()).isEqualTo("testContent");
        assertThat(message.getDataHandler().getContentType()).isEqualTo("text/html;charset=UTF-8");
    }

    @Test
    void testSendMultipartEmail() throws Exception {
        mailService.sendEmail("john.doe@example.com", "testSubject", "testContent", true, false);
        verify(javaMailSender).send(messageCaptor.capture());
        MimeMessage message = messageCaptor.getValue();
        MimeMultipart mp = (MimeMultipart) message.getContent();
        MimeBodyPart part = (MimeBodyPart) ((MimeMultipart) mp.getBodyPart(0).getContent()).getBodyPart(0);
        ByteArrayOutputStream aos = new ByteArrayOutputStream();
        part.writeTo(aos);
        assertThat(message.getSubject()).isEqualTo("testSubject");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("john.doe@example.com");
        assertThat(message.getFrom()[0].toString()).isEqualTo(applicationProperties.getMail().getFrom());
        assertThat(message.getContent()).isInstanceOf(Multipart.class);
        assertThat(aos.toString()).isEqualTo("\r\ntestContent");
        assertThat(part.getDataHandler().getContentType()).isEqualTo("text/plain; charset=UTF-8");
    }

    @Test
    void testSendMultipartHtmlEmail() throws Exception {
        mailService.sendEmail("john.doe@example.com", "testSubject", "testContent", true, true);
        verify(javaMailSender).send(messageCaptor.capture());
        MimeMessage message = messageCaptor.getValue();
        MimeMultipart mp = (MimeMultipart) message.getContent();
        MimeBodyPart part = (MimeBodyPart) ((MimeMultipart) mp.getBodyPart(0).getContent()).getBodyPart(0);
        ByteArrayOutputStream aos = new ByteArrayOutputStream();
        part.writeTo(aos);
        assertThat(message.getSubject()).isEqualTo("testSubject");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("john.doe@example.com");
        assertThat(message.getFrom()[0].toString()).isEqualTo(applicationProperties.getMail().getFrom());
        assertThat(message.getContent()).isInstanceOf(Multipart.class);
        assertThat(aos.toString()).isEqualTo("\r\ntestContent");
        assertThat(part.getDataHandler().getContentType()).isEqualTo("text/html;charset=UTF-8");
    }

    @Test
    void testSendEmailFromTemplate() throws Exception {
        User user = new User();
        user.setLogin("john");
        user.setEmail("john.doe@example.com");
        user.setLangKey("en");
        mailService.sendEmailFromTemplate(user, "mail/testEmail", "email.test.title");
        verify(javaMailSender).send(messageCaptor.capture());
        MimeMessage message = messageCaptor.getValue();
        assertThat(message.getSubject()).isEqualTo("test title");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(user.getEmail());
        assertThat(message.getFrom()[0].toString()).isEqualTo(applicationProperties.getMail().getFrom());
        assertThat(message.getContent().toString()).isEqualToNormalizingNewlines("<html>test title, http://127.0.0.1:8080, john</html>\n");
        assertThat(message.getDataHandler().getContentType()).isEqualTo("text/html;charset=UTF-8");
    }

    @Test
    void testSendActivationEmail() throws Exception {
        User user = new User();
        user.setLangKey(Constants.DEFAULT_LANGUAGE);
        user.setLogin("john");
        user.setEmail("john.doe@example.com");
        mailService.sendActivationEmail(user);
        verify(javaMailSender).send(messageCaptor.capture());
        MimeMessage message = messageCaptor.getValue();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(user.getEmail());
        assertThat(message.getFrom()[0].toString()).isEqualTo(applicationProperties.getMail().getFrom());
        assertThat(message.getContent().toString()).isNotEmpty();
        assertThat(message.getDataHandler().getContentType()).isEqualTo("text/html;charset=UTF-8");
    }

    @Test
    void testCreationEmail() throws Exception {
        User user = new User();
        user.setLangKey(Constants.DEFAULT_LANGUAGE);
        user.setLogin("john");
        user.setEmail("john.doe@example.com");
        mailService.sendCreationEmail(user);
        verify(javaMailSender).send(messageCaptor.capture());
        MimeMessage message = messageCaptor.getValue();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(user.getEmail());
        assertThat(message.getFrom()[0].toString()).isEqualTo(applicationProperties.getMail().getFrom());
        assertThat(message.getContent().toString()).isNotEmpty();
        assertThat(message.getDataHandler().getContentType()).isEqualTo("text/html;charset=UTF-8");

        user.setLangKey("pl");
        mailService.sendCreationEmail(user);
        verify(javaMailSender, atLeastOnce()).send(messageCaptor.capture());
        message = messageCaptor.getValue();
        assertThat(message.getContent().toString()).contains("kliknij poniższy link");
    }

    @Test
    void testSendPasswordResetMail() throws Exception {
        User user = new User();
        user.setLangKey(Constants.DEFAULT_LANGUAGE);
        user.setLogin("john");
        user.setEmail("john.doe@example.com");
        mailService.sendPasswordResetMail(user);
        verify(javaMailSender).send(messageCaptor.capture());
        MimeMessage message = messageCaptor.getValue();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(user.getEmail());
        assertThat(message.getFrom()[0].toString()).isEqualTo(applicationProperties.getMail().getFrom());
        assertThat(message.getContent().toString()).isNotEmpty();
        assertThat(message.getDataHandler().getContentType()).isEqualTo("text/html;charset=UTF-8");
    }

    @Test
    void testSendEmailWithException() throws Exception {
        doThrow(MailSendException.class).when(javaMailSender).send(any(MimeMessage.class));
        mailService.sendEmail("john.doe@example.com", "testSubject", "testContent", false, false);
    }

    private static User reminderUser() {
        User user = new User();
        user.setLogin("john");
        user.setEmail("john.doe@example.com");
        return user;
    }

    private static Vehicle reminderVehicle() {
        return Vehicle.builder()
            .make("Toyota")
            .model("Corolla")
            .licensePlate("WX 12345")
            .build();
    }

    @Test
    void testSendRoutineServiceReminderEmail() throws Exception {
        User user = reminderUser();
        Vehicle vehicle = reminderVehicle();
        RoutineService routineService = RoutineService.builder()
            .nextByDate(REMINDER_DATE)
            .details("Oil change")
            .build();
        SoftAssertions softly = new SoftAssertions();

        for (int i = 0; i < LANGUAGES.length; i++) {
            String langKey = LANGUAGES[i];
            user.setLangKey(langKey);
            mailService.sendRoutineServiceReminderEmail(user, vehicle, routineService, 3);
            verify(javaMailSender, times(i + 1)).send(messageCaptor.capture());
            MimeMessage message = messageCaptor.getValue();
            String content = message.getContent().toString();

            softly.assertThat(message.getSubject()).isEqualTo(langKey.equals("en")
                ? "CarCare - service reminder"
                : "CarCare - przypomnienie o obsłudze");
            softly.assertThat(content).contains(langKey.equals("en")
                ? "Hello john,"
                : "Cześć john,");
            softly.assertThat(content).contains(langKey.equals("en")
                ? "Please bear in mind that in 3 days i.e. 2026-04-18 the following services should be carried out for your vehicle Toyota Corolla (WX 12345): Oil change."
                : "W systemie zaznaczono, że w pojeździe Toyota Corolla (WX 12345) w ciągu 3 dni, tj. 2026-04-18, powinna zostać wykonana następująca obsługa: Oil change.");
        }
        softly.assertAll();
    }

    @Test
    void testSendInsuranceReminderEmail() throws Exception {
        User user = reminderUser();
        Vehicle vehicle = reminderVehicle();
        Insurance insurance = Insurance.builder()
            .validThru(REMINDER_DATE)
            .build();
        SoftAssertions softly = new SoftAssertions();

        for (int i = 0; i < LANGUAGES.length; i++) {
            String langKey = LANGUAGES[i];
            user.setLangKey(langKey);
            mailService.sendInsuranceReminderEmail(user, vehicle, insurance, 3);
            verify(javaMailSender, times(i + 1)).send(messageCaptor.capture());
            MimeMessage message = messageCaptor.getValue();
            String content = message.getContent().toString();

            softly.assertThat(message.getSubject()).isEqualTo(langKey.equals("en")
                ? "CarCare - insurance reminder"
                : "CarCare - przypomnienie o ubezpieczeniu");
            softly.assertThat(content).contains(langKey.equals("en")
                ? "Hello john,"
                : "Cześć john,");
            softly.assertThat(content).contains(langKey.equals("en")
                ? "Please bear in mind that the insurance for your vehicle Toyota Corolla (WX 12345) will expire in 3 days i.e. 2026-04-18."
                : "Ważność polisy ubezpieczeniowej dla Twojego pojazdu Toyota Corolla (WX 12345) zakończy się w ciągu 3 dni, tj. 2026-04-18.");
        }
        softly.assertAll();
    }

    @Test
    void testSendInspectionReminderEmail() throws Exception {
        User user = reminderUser();
        Vehicle vehicle = reminderVehicle();
        Inspection inspection = Inspection.builder()
            .validThru(REMINDER_DATE)
            .build();
        SoftAssertions softly = new SoftAssertions();

        for (int i = 0; i < LANGUAGES.length; i++) {
            String langKey = LANGUAGES[i];
            user.setLangKey(langKey);
            mailService.sendInspectionReminderEmail(user, vehicle, inspection, 3);
            verify(javaMailSender, times(i + 1)).send(messageCaptor.capture());
            MimeMessage message = messageCaptor.getValue();
            String content = message.getContent().toString();

            softly.assertThat(message.getSubject()).isEqualTo(langKey.equals("en")
                ? "CarCare - inspection reminder"
                : "CarCare - przypomnienie o przeglądzie");
            softly.assertThat(content).contains(langKey.equals("en")
                ? "Hello john,"
                : "Cześć john,");
            softly.assertThat(content).contains(langKey.equals("en")
                ? "Please bear in mind that the technical inspection of your vehicle Toyota Corolla (WX 12345) should be carried out in 3 days i.e. 2026-04-18."
                : "Ważność przeglądu technicznego Twojego pojazdu Toyota Corolla (WX 12345) zakończy się w ciągu 3 dni, tj. 2026-04-18.");
        }
        softly.assertAll();
    }

    @Test
    void testSendLocalizedEmailForAllSupportedLanguages() throws Exception {
        User user = new User();
        user.setLogin("john");
        user.setEmail("john.doe@example.com");
        for (String langKey : LANGUAGES) {
            user.setLangKey(langKey);
            mailService.sendEmailFromTemplate(user, "mail/testEmail", "email.test.title");
            verify(javaMailSender, atLeastOnce()).send(messageCaptor.capture());
            MimeMessage message = messageCaptor.getValue();

            String propertyFilePath = "i18n/test-messages_" + getJavaLocale(langKey) + ".properties";
            URL resource = this.getClass().getClassLoader().getResource(propertyFilePath);
            File file = new File(new URI(resource.getFile()).getPath());
            Properties properties = new Properties();
            properties.load(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));

            String emailTitle = (String) properties.get("email.test.title");
            assertThat(message.getSubject()).isEqualTo(emailTitle);
            assertThat(message.getContent().toString()).isEqualToNormalizingNewlines("<html>" + emailTitle + ", http://127.0.0.1:8080, john</html>\n");
        }
    }

    /**
     * Convert a lang key to the Java locale.
     */
    private String getJavaLocale(String langKey) {
        String javaLangKey = langKey;
        Matcher matcher2 = PATTERN_LOCALE_2.matcher(langKey);
        if (matcher2.matches()) {
            javaLangKey = matcher2.group(1) + "_"+ matcher2.group(2).toUpperCase();
        }
        Matcher matcher3 = PATTERN_LOCALE_3.matcher(langKey);
        if (matcher3.matches()) {
            javaLangKey = matcher3.group(1) + "_" + matcher3.group(2) + "_" + matcher3.group(3).toUpperCase();
        }
        return javaLangKey;
    }
}
