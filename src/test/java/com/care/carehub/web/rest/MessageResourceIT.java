package com.care.carehub.web.rest;

import com.care.carehub.CarehubApp;
import com.care.carehub.domain.Message;
import com.care.carehub.repository.MessageRepository;
import com.care.carehub.service.MessageService;
import com.care.carehub.web.rest.errors.ExceptionTranslator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.care.carehub.web.rest.TestUtil.createFormattingConversionService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the {@link MessageResource} REST controller.
 */
@SpringBootTest(classes = CarehubApp.class)
public class MessageResourceIT {

    private static final Long DEFAULT_RECIPIENT_ID = 1L;
    private static final Long UPDATED_RECIPIENT_ID = 2L;

    private static final Long DEFAULT_POSTED_BY_MEMBER_ID = 1L;
    private static final Long UPDATED_POSTED_BY_MEMBER_ID = 2L;

    private static final String DEFAULT_MESSAGE_BODY = "AAAAAAAAAA";
    private static final String UPDATED_MESSAGE_BODY = "BBBBBBBBBB";

    private static final Instant DEFAULT_WHEN_CREATED = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_WHEN_CREATED = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MessageService messageService;

    @Autowired
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Autowired
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    @Autowired
    private ExceptionTranslator exceptionTranslator;

    @Autowired
    private EntityManager em;

    @Autowired
    private Validator validator;

    private MockMvc restMessageMockMvc;

    private Message message;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
        final MessageResource messageResource = new MessageResource(messageService);
        this.restMessageMockMvc = MockMvcBuilders.standaloneSetup(messageResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setControllerAdvice(exceptionTranslator)
            .setConversionService(createFormattingConversionService())
            .setMessageConverters(jacksonMessageConverter)
            .setValidator(validator).build();
    }

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Message createEntity(EntityManager em) {
        Message message = new Message()
            .recipientId(DEFAULT_RECIPIENT_ID)
            .postedByMemberId(DEFAULT_POSTED_BY_MEMBER_ID)
            .messageBody(DEFAULT_MESSAGE_BODY)
            .whenCreated(DEFAULT_WHEN_CREATED);
        return message;
    }
    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Message createUpdatedEntity(EntityManager em) {
        Message message = new Message()
            .recipientId(UPDATED_RECIPIENT_ID)
            .postedByMemberId(UPDATED_POSTED_BY_MEMBER_ID)
            .messageBody(UPDATED_MESSAGE_BODY)
            .whenCreated(UPDATED_WHEN_CREATED);
        return message;
    }

    @BeforeEach
    public void initTest() {
        message = createEntity(em);
    }

    @Test
    @Transactional
    public void createMessage() throws Exception {
        int databaseSizeBeforeCreate = messageRepository.findAll().size();

        // Create the Message
        restMessageMockMvc.perform(post("/api/messages")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(message)))
            .andExpect(status().isCreated());

        // Validate the Message in the database
        List<Message> messageList = messageRepository.findAll();
        assertThat(messageList).hasSize(databaseSizeBeforeCreate + 1);
        Message testMessage = messageList.get(messageList.size() - 1);
        assertThat(testMessage.getRecipientId()).isEqualTo(DEFAULT_RECIPIENT_ID);
        assertThat(testMessage.getPostedByMemberId()).isEqualTo(DEFAULT_POSTED_BY_MEMBER_ID);
        assertThat(testMessage.getMessageBody()).isEqualTo(DEFAULT_MESSAGE_BODY);
        assertThat(testMessage.getWhenCreated()).isEqualTo(DEFAULT_WHEN_CREATED);
    }

    @Test
    @Transactional
    public void createMessageWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = messageRepository.findAll().size();

        // Create the Message with an existing ID
        message.setId(1L);

        // An entity with an existing ID cannot be created, so this API call must fail
        restMessageMockMvc.perform(post("/api/messages")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(message)))
            .andExpect(status().isBadRequest());

        // Validate the Message in the database
        List<Message> messageList = messageRepository.findAll();
        assertThat(messageList).hasSize(databaseSizeBeforeCreate);
    }


    @Test
    @Transactional
    public void checkPostedByMemberIdIsRequired() throws Exception {
        int databaseSizeBeforeTest = messageRepository.findAll().size();
        // set the field null
        message.setPostedByMemberId(null);

        // Create the Message, which fails.

        restMessageMockMvc.perform(post("/api/messages")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(message)))
            .andExpect(status().isBadRequest());

        List<Message> messageList = messageRepository.findAll();
        assertThat(messageList).hasSize(databaseSizeBeforeTest);
    }

    @Test
    @Transactional
    public void getAllMessages() throws Exception {
        // Initialize the database
        messageRepository.saveAndFlush(message);

        // Get all the messageList
        restMessageMockMvc.perform(get("/api/messages?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(message.getId().intValue())))
            .andExpect(jsonPath("$.[*].recipientId").value(hasItem(DEFAULT_RECIPIENT_ID.intValue())))
            .andExpect(jsonPath("$.[*].postedByMemberId").value(hasItem(DEFAULT_POSTED_BY_MEMBER_ID.intValue())))
            .andExpect(jsonPath("$.[*].messageBody").value(hasItem(DEFAULT_MESSAGE_BODY)))
            .andExpect(jsonPath("$.[*].whenCreated").value(hasItem(DEFAULT_WHEN_CREATED.toString())));
    }
    
    @Test
    @Transactional
    public void getMessage() throws Exception {
        // Initialize the database
        messageRepository.saveAndFlush(message);

        // Get the message
        restMessageMockMvc.perform(get("/api/messages/{id}", message.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .andExpect(jsonPath("$.id").value(message.getId().intValue()))
            .andExpect(jsonPath("$.recipientId").value(DEFAULT_RECIPIENT_ID.intValue()))
            .andExpect(jsonPath("$.postedByMemberId").value(DEFAULT_POSTED_BY_MEMBER_ID.intValue()))
            .andExpect(jsonPath("$.messageBody").value(DEFAULT_MESSAGE_BODY))
            .andExpect(jsonPath("$.whenCreated").value(DEFAULT_WHEN_CREATED.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingMessage() throws Exception {
        // Get the message
        restMessageMockMvc.perform(get("/api/messages/{id}", Long.MAX_VALUE))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateMessage() throws Exception {
        // Initialize the database
        messageService.save(message);

        int databaseSizeBeforeUpdate = messageRepository.findAll().size();

        // Update the message
        Message updatedMessage = messageRepository.findById(message.getId()).get();
        // Disconnect from session so that the updates on updatedMessage are not directly saved in db
        em.detach(updatedMessage);
        updatedMessage
            .recipientId(UPDATED_RECIPIENT_ID)
            .postedByMemberId(UPDATED_POSTED_BY_MEMBER_ID)
            .messageBody(UPDATED_MESSAGE_BODY)
            .whenCreated(UPDATED_WHEN_CREATED);

        restMessageMockMvc.perform(put("/api/messages")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(updatedMessage)))
            .andExpect(status().isOk());

        // Validate the Message in the database
        List<Message> messageList = messageRepository.findAll();
        assertThat(messageList).hasSize(databaseSizeBeforeUpdate);
        Message testMessage = messageList.get(messageList.size() - 1);
        assertThat(testMessage.getRecipientId()).isEqualTo(UPDATED_RECIPIENT_ID);
        assertThat(testMessage.getPostedByMemberId()).isEqualTo(UPDATED_POSTED_BY_MEMBER_ID);
        assertThat(testMessage.getMessageBody()).isEqualTo(UPDATED_MESSAGE_BODY);
        assertThat(testMessage.getWhenCreated()).isEqualTo(UPDATED_WHEN_CREATED);
    }

    @Test
    @Transactional
    public void updateNonExistingMessage() throws Exception {
        int databaseSizeBeforeUpdate = messageRepository.findAll().size();

        // Create the Message

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restMessageMockMvc.perform(put("/api/messages")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(message)))
            .andExpect(status().isBadRequest());

        // Validate the Message in the database
        List<Message> messageList = messageRepository.findAll();
        assertThat(messageList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    public void deleteMessage() throws Exception {
        // Initialize the database
        messageService.save(message);

        int databaseSizeBeforeDelete = messageRepository.findAll().size();

        // Delete the message
        restMessageMockMvc.perform(delete("/api/messages/{id}", message.getId())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Message> messageList = messageRepository.findAll();
        assertThat(messageList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    public void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Message.class);
        Message message1 = new Message();
        message1.setId(1L);
        Message message2 = new Message();
        message2.setId(message1.getId());
        assertThat(message1).isEqualTo(message2);
        message2.setId(2L);
        assertThat(message1).isNotEqualTo(message2);
        message1.setId(null);
        assertThat(message1).isNotEqualTo(message2);
    }
}
