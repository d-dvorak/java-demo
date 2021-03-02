package com.gamifika.mail.service;

import com.gamifika.mail.domain.AktivnePozvanky;
import com.gamifika.mail.model.PozvankaDto;
import com.gamifika.mail.repository.PozvankaRepository;
import freemarker.core.ParseException;
import javax.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;
import org.dozer.Mapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

/**
 * Mail service presented in my bachelor thesis.
 * The logic is pretty straightforward - send an invitation based on the recipient information received from frontend application.
 * The sender uses html templates to send email. This way, the template is easily changeable and also the service and be easily expandable.
 *
 * Again, there are mixed languages - english, czech and even slovak. The reason for the third one is the data model being made in cooperation with Slovak colleague.
 *
 * @author David Dvořák
 * 2019
 */
@Service
@Transactional
public class MailServiceImplementation implements MailService{    

    @Autowired
    JavaMailSender sender;

    @Autowired
    Configuration freemarkerConfig;
    
    @Autowired
    PozvankaRepository pozvankaRepository;
    
    @Autowired
    Mapper mapper;

    /*
    Receive recipient information, prepare template and use the mail library to send an invitation e-mail.

    Everything should've been in english, in future refactoring, I eventually changed it.
     */
    @Override
    public void sendInvitation(PozvankaDto pozvankaDto, Long idParent) {      
       
        try {
            AktivnePozvanky pozvanka = mapper.map(pozvankaDto, AktivnePozvanky.class);
            pozvanka.setDatumVytvorenia(new Date());
            pozvanka.setHashString(generateHash(idParent.toString()));
            pozvanka.setPlatnost(true);
            pozvanka.setIdRole(97L);
            
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message,true);
            
            //přiřazení proměnných v šabloně
            Map<String, Object> model = new HashMap();
            model.put("name", pozvankaDto.getJmenoVytvoreneHracom());
            model.put("hash", pozvanka.getHashString());
            
            freemarkerConfig.setClassForTemplateLoading(this.getClass(), "/");
            Template t = freemarkerConfig.getTemplate("invitation.ftl");
            String text = FreeMarkerTemplateUtils.processTemplateIntoString(t, model);
            
            //upravit adresu prijemce
            helper.setTo(pozvankaDto.getPrijemce());
            helper.setFrom("gamifikaoe@gmail.com");
            helper.setText(text, true); //bool = html
            helper.setSubject("Pozvánka");

            ClassPathResource file = new ClassPathResource("postapo.jpg");
            helper.addInline("id101", file);

            sender.send(message);
                  
            pozvankaRepository.save(pozvanka);
        } catch (MalformedTemplateNameException ex) {
            Logger.getLogger(MailServiceImplementation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ParseException | TemplateException | MessagingException ex) {
            Logger.getLogger(MailServiceImplementation.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(MailServiceImplementation.class.getName()).log(Level.SEVERE, null, ex);
        }

    }        

    // The following code is mostly done with the help of internet forums and tutorials.
    private String generateHash(String input){        
        
        try{  
            Date date = new Date();
            Timestamp timestamp = new Timestamp(date.getTime());
            String dateString = timestamp.toString();                           
            
            input = dateString + input;
            
            MessageDigest md = MessageDigest.getInstance("SHA-512");             
            byte[] messageDigest = md.digest(input.getBytes());             
            BigInteger no = new BigInteger(1, messageDigest); 
            String hashtext = no.toString(16); 
            
            while (hashtext.length() < 32) { 
                hashtext = "0" + hashtext; 
            } 
            return hashtext; 
            
        }catch(NoSuchAlgorithmException e){
            
            throw new RuntimeException(e);
        }
        
    }

}
