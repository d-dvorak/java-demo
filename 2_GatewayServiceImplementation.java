package com.gamifika.oeamazongateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.gamifika.oeamazongateway.domain.Consumption;
import com.gamifika.oeamazongateway.domain.NewUser;
import com.gamifika.oeamazongateway.domain.NisLevelUp;
import com.gamifika.oeamazongateway.domain.Saving;
import com.gamifika.oeamazongateway.domain.Activity;
import com.gamifika.oeamazongateway.domain.Client;
import com.gamifika.oeamazongateway.repository.ActivityRepository;
import com.gamifika.oeamazongateway.repository.ClientRepository;
import com.gamifika.oeamazongateway.repository.ConsumptionRepository;
import com.gamifika.oeamazongateway.repository.NewUserRepository;
import com.gamifika.oeamazongateway.repository.NisLevelUpRepository;
import com.gamifika.oeamazongateway.repository.SavingRepository;
import com.gamifika.oeamazongateway.repository.ZpravaRepository;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JavaEE example, this code is from my bachelor thesis (czech comments are original)
 * It was written in Java 8, works with spring-boot framework and uses hibernate for persisting entities.
 * The purpose of this service was to receive data messages from a different system by using Amazon SQS service.
 * Almost the entire SQS client configuration is not worth mentioning, since it was mostly glued up code from official documentation and some tutorials.
 * The following code receives the message, parses it and saves it into the database.
 *
 *
 * Just like before, there are czech words in the code, which I find to be a great mistake.
 * Luckily I remade this to work differently later.
 *
 * @author David Dvořák
 * 2019
 */
@Service
@Transactional
public class GatewayServiceImplementation implements GatewayService {      

    // Autowired repositories, used to save different types of messages, all were connected to the Message entity persisting metadata.
    // Later in the code, I used switch. Which I find to be completely wrong. The database model was designed by me, and I was not considering many things about the business logic.
    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private NewUserRepository newUserRepository;

    @Autowired
    private ZpravaRepository zpravaRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private SavingRepository savingRepository;

    @Autowired
    private ConsumptionRepository consumptionRepository;
    
    @Autowired
    private NisLevelUpRepository nisLevelUpRepository;

    /*
    The original message needed to be converted into json for the transformation process. For this, I used the Jackson library.
     */
    @Override
    public String xmlToJsonString(String xmlZprava) {   // should've been private
        
        try {
            // Create a new XmlMapper to read XML tags
            XmlMapper xmlMapper = new XmlMapper();            
            //Reading the XML
            JsonNode jsonNode = xmlMapper.readTree(xmlZprava.getBytes());            
            //Create a new ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();            
            //Get JSON as a string
            String value = objectMapper.writeValueAsString(jsonNode); // could've returned the value in one line
            return value;
            
        } catch (IOException ex) {
            Logger.getLogger(GatewayServiceImplementation.class.getName()).log(Level.SEVERE, null, ex); // missing message
        }
        return null; // honestly, very stupid, this case is not handled anywhere

    }

    /*
    The format of the message to be saved needed to be completely different from the format received.
    I was parsing the string to change the structure into the desired form. Luckily a remade this later while working for Gamifika.
     */
    @Override
    public String parsovani(String value) {       // should've been private
            
            //index pro budoucí zakončení metadat
            int dataIndex = value.indexOf(",\"data\"");            
            //smazat tag 'data' ze stringu
            String updatedValue = value.replace("\"data\":{","").replaceFirst("}", "");            
            //přidání tagu metadata          
            updatedValue = updatedValue.substring(0, dataIndex) + "}" + updatedValue.substring(dataIndex, updatedValue.length());
            updatedValue = "{\"metadata\":{" + updatedValue.substring(1);
            
            return updatedValue;
    }

    /*

     */
    @Override
    public void ukladani(String prichoziZprava) {
              
        prichoziZprava = parsovani(xmlToJsonString(prichoziZprava));
                
        //rozpoznání typu zprávy do proměnné targetDataTypeClass                      
        int startMsgType = prichoziZprava.indexOf("messageType")+14; //spacing
        String targetDataTypeClass = prichoziZprava.substring(
                startMsgType,prichoziZprava.indexOf("\"", startMsgType) //spacing, indentation
            );     
        
        //převedení string zprávy na json + mapování na konkrétní entitu v závislosti na typu zprávy
        ObjectMapper objectMapper = new ObjectMapper();        
        try {    
            
            JsonNode jsonNode = objectMapper.readTree(prichoziZprava);
            switch(targetDataTypeClass){ // should've mapped this entire thing from the other way around, used message which had relationship with each of these.
                case "newUser": 
                    NewUser newUser = objectMapper.convertValue(jsonNode,NewUser.class);
                    newUserRepository.save(newUser); 
                    break;                    
                case "nisLevelUp":  
                    NisLevelUp nisLevelUp = objectMapper.convertValue(jsonNode,NisLevelUp.class);
                    nisLevelUpRepository.save(nisLevelUp); 
                    break;
                case "saving":  
                    Saving saving = objectMapper.convertValue(jsonNode,Saving.class);
                    savingRepository.save(saving); 
                    break;
                case "consumption":
                    Consumption consumption = objectMapper.convertValue(jsonNode,Consumption.class);
                    consumptionRepository.save(consumption); 
                    break;
                case "client":
                    Client client = objectMapper.convertValue(jsonNode,Client.class);
                    clientRepository.save(client); 
                    break;
                case "activity":
                    Activity activity = objectMapper.convertValue(jsonNode,Activity.class);
                    activityRepository.save(activity); 
                    break;
                default:    
            }    
            
        } catch (IOException ex) {
            Logger.getLogger(GatewayServiceImplementation.class.getName()).log(Level.SEVERE, null, ex);
        }  
        
    }
            
}
