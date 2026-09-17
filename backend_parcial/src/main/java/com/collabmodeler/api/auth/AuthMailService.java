package com.collabmodeler.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AuthMailService {
    private final JavaMailSender sender;
    private final String publicUrl;
    public AuthMailService(JavaMailSender sender, @Value("${app.public-url}") String publicUrl) {
        this.sender = sender; this.publicUrl = publicUrl.replaceAll("/$", "");
    }
    public void verification(String email, String token) { send(email, "Verifica tu cuenta de Collab Modeler", "Abre este enlace para verificar tu correo: " + publicUrl + "/verify?token=" + token); }
    public void reset(String email, String token) { send(email, "Recupera tu cuenta de Collab Modeler", "Abre este enlace para cambiar tu contraseña: " + publicUrl + "/reset-password?token=" + token); }
    public void invitation(String email, String token) { send(email, "Invitación a Collab Modeler", "Completa tu registro: " + publicUrl + "/register?invitation=" + token); }
    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage(); message.setTo(to); message.setSubject(subject); message.setText(text); sender.send(message);
    }
}
