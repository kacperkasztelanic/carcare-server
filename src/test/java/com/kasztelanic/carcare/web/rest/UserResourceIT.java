package com.kasztelanic.carcare.web.rest;

import com.kasztelanic.carcare.CarcareApp;
import com.kasztelanic.carcare.domain.Authority;
import com.kasztelanic.carcare.domain.User;
import com.kasztelanic.carcare.repository.AuthorityRepository;
import com.kasztelanic.carcare.repository.UserRepository;
import com.kasztelanic.carcare.security.AuthoritiesConstants;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.kasztelanic.carcare.service.MailService;
import com.kasztelanic.carcare.service.UserService;
import com.kasztelanic.carcare.service.dto.UserDto;
import com.kasztelanic.carcare.service.mapper.UserMapper;
import com.kasztelanic.carcare.web.rest.vm.ManagedUserVm;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for the {@link UserResource} REST controller.
 */
@SpringBootTest(classes = CarcareApp.class)
@AutoConfigureMockMvc
@WithMockUser(authorities = AuthoritiesConstants.ADMIN)
class UserResourceIT {

    private static final String DEFAULT_LOGIN = "johndoe";
    private static final String CREATED_LOGIN = "john@doe";
    private static final String CREATED_EMAIL = "john@doe.local";
    private static final String UPDATED_LOGIN = "jhipster";

    private static final Long DEFAULT_ID = 1L;

    private static final String DEFAULT_PASSWORD = "passjohndoe";
    private static final String UPDATED_PASSWORD = "passjhipster";

    private static final String DEFAULT_EMAIL = "johndoe@localhost";
    private static final String UPDATED_EMAIL = "jhipster@localhost";

    private static final String DEFAULT_FIRSTNAME = "john";
    private static final String UPDATED_FIRSTNAME = "jhipsterFirstName";

    private static final String DEFAULT_LASTNAME = "doe";
    private static final String UPDATED_LASTNAME = "jhipsterLastName";

    private static final String DEFAULT_IMAGEURL = "http://placehold.it/50x50";
    private static final String UPDATED_IMAGEURL = "http://placehold.it/40x40";

    private static final String DEFAULT_LANGKEY = "en";
    private static final String UPDATED_LANGKEY = "fr";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthorityRepository authorityRepository;
    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private EntityManager em;
    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private MailService mailService;

    private User user;

    @BeforeEach
    void setup() {
        cacheManager.getCache(UserRepository.USERS_BY_LOGIN_CACHE).clear();
        cacheManager.getCache(UserRepository.USERS_BY_EMAIL_CACHE).clear();
    }

    /**
     * Create a User.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which has a required relationship to the User entity.
     */
    static User createEntity(EntityManager em) {
        User user = new User();
        user.setLogin(DEFAULT_LOGIN + RandomStringUtils.randomAlphabetic(5));
        user.setPassword(RandomStringUtils.random(60));
        user.setActivated(true);
        user.setEmail(RandomStringUtils.randomAlphabetic(5) + DEFAULT_EMAIL);
        user.setFirstName(DEFAULT_FIRSTNAME);
        user.setLastName(DEFAULT_LASTNAME);
        user.setImageUrl(DEFAULT_IMAGEURL);
        user.setLangKey(DEFAULT_LANGKEY);
        return user;
    }

    @BeforeEach
    void initTest() {
        user = createEntity(em);
        user.setLogin(DEFAULT_LOGIN);
        user.setEmail(DEFAULT_EMAIL);
    }

    @Test
    @Transactional
    void createUser() throws Exception {
        int databaseSizeBeforeCreate = userRepository.findAll().size();

        // Create the User
        ManagedUserVm managedUserVM = new ManagedUserVm();
        managedUserVM.setLogin(CREATED_LOGIN);
        managedUserVM.setPassword(DEFAULT_PASSWORD);
        managedUserVM.setFirstName(DEFAULT_FIRSTNAME);
        managedUserVM.setLastName(DEFAULT_LASTNAME);
        managedUserVM.setEmail(CREATED_EMAIL);
        managedUserVM.setActivated(true);
        managedUserVM.setImageUrl(DEFAULT_IMAGEURL);
        managedUserVM.setLangKey(DEFAULT_LANGKEY);
        managedUserVM.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        mockMvc.perform(post("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(managedUserVM)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/users/" + CREATED_LOGIN))
            .andExpect(header().string("X-carcare-alert", "userManagement.created"))
            .andExpect(header().string("X-carcare-params", "john%40doe"))
            .andExpect(jsonPath("$.login").value(CREATED_LOGIN))
            .andExpect(jsonPath("$.firstName").value(DEFAULT_FIRSTNAME))
            .andExpect(jsonPath("$.lastName").value(DEFAULT_LASTNAME))
            .andExpect(jsonPath("$.email").value(CREATED_EMAIL))
            .andExpect(jsonPath("$.activated").value(true))
            .andExpect(jsonPath("$.langKey").value(DEFAULT_LANGKEY))
            .andExpect(jsonPath("$.imageUrl").value(DEFAULT_IMAGEURL))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.authorities").doesNotExist());

        // Validate the User in the database
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeCreate + 1);
        User testUser = userList.get(userList.size() - 1);
        assertThat(testUser.getLogin()).isEqualTo(CREATED_LOGIN);
        assertThat(testUser.getFirstName()).isEqualTo(DEFAULT_FIRSTNAME);
        assertThat(testUser.getLastName()).isEqualTo(DEFAULT_LASTNAME);
        assertThat(testUser.getEmail()).isEqualTo(CREATED_EMAIL);
        assertThat(testUser.getImageUrl()).isEqualTo(DEFAULT_IMAGEURL);
        assertThat(testUser.getLangKey()).isEqualTo(DEFAULT_LANGKEY);
        assertThat(testUser.isActivated()).isTrue();
        assertThat(testUser.getPassword()).hasSize(60).isNotEqualTo(DEFAULT_PASSWORD);
        assertThat(testUser.getResetKey()).isNotBlank();
        verify(mailService, times(1)).sendCreationEmail(any(User.class));
    }

    @Test
    @Transactional
    void createUserWithEmptyLanguageKeyUsesDefaultLanguage() throws Exception {
        mockMvc.perform(post("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content("{\"login\":\"empty-lang\",\"firstName\":\"Empty\",\"lastName\":\"Language\","
                + "\"email\":\"empty-lang@example.com\",\"activated\":true,\"langKey\":\"\","
                + "\"authorities\":[\"ROLE_USER\"]}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.login").value("empty-lang"))
            .andExpect(jsonPath("$.langKey").value(DEFAULT_LANGKEY));
    }

    @Test
    @Transactional
    void updateUserWithEmptyLanguageKeyKeepsLookupsUsable() throws Exception {
        User target = userRepository.findOneByLogin("user").orElseThrow();

        mockMvc.perform(put("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content("{\"id\":" + target.getId() + ",\"login\":\"user\","
                + "\"email\":\"user@localhost\",\"activated\":true,\"langKey\":\"\","
                + "\"authorities\":[\"ROLE_USER\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.langKey").value(DEFAULT_LANGKEY));

        userRepository.flush();
        assertThat(userRepository.findOneByLogin("user").orElseThrow().getLangKey())
            .isEqualTo(DEFAULT_LANGKEY);

        // A null lang_key would make Locale.forLanguageTag NPE here and return 500.
        mockMvc.perform(get("/api/fuel-type").with(user("user").authorities(
            new SimpleGrantedAuthority(AuthoritiesConstants.USER))))
            .andExpect(status().isOk());
    }

    @Test
    @Transactional
    void createUserWithExistingId() throws Exception {
        int databaseSizeBeforeCreate = userRepository.findAll().size();

        ManagedUserVm managedUserVM = new ManagedUserVm();
        managedUserVM.setId(1L);
        managedUserVM.setLogin(DEFAULT_LOGIN);
        managedUserVM.setPassword(DEFAULT_PASSWORD);
        managedUserVM.setFirstName(DEFAULT_FIRSTNAME);
        managedUserVM.setLastName(DEFAULT_LASTNAME);
        managedUserVM.setEmail(DEFAULT_EMAIL);
        managedUserVM.setActivated(true);
        managedUserVM.setImageUrl(DEFAULT_IMAGEURL);
        managedUserVM.setLangKey(DEFAULT_LANGKEY);
        managedUserVM.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        // An entity with an existing ID cannot be created, so this API call must fail
        mockMvc.perform(post("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(managedUserVM)))
            .andExpect(status().isBadRequest());

        // Validate the User in the database
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void createUserWithExistingLogin() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);
        int databaseSizeBeforeCreate = userRepository.findAll().size();

        ManagedUserVm managedUserVM = new ManagedUserVm();
        managedUserVM.setLogin(DEFAULT_LOGIN);// this login should already be used
        managedUserVM.setPassword(DEFAULT_PASSWORD);
        managedUserVM.setFirstName(DEFAULT_FIRSTNAME);
        managedUserVM.setLastName(DEFAULT_LASTNAME);
        managedUserVM.setEmail("anothermail@localhost");
        managedUserVM.setActivated(true);
        managedUserVM.setImageUrl(DEFAULT_IMAGEURL);
        managedUserVM.setLangKey(DEFAULT_LANGKEY);
        managedUserVM.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        // Create the User
        mockMvc.perform(post("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(managedUserVM)))
            .andExpect(status().isBadRequest());

        // Validate the User in the database
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void createUserWithExistingEmail() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);
        int databaseSizeBeforeCreate = userRepository.findAll().size();

        ManagedUserVm managedUserVM = new ManagedUserVm();
        managedUserVM.setLogin("anotherlogin");
        managedUserVM.setPassword(DEFAULT_PASSWORD);
        managedUserVM.setFirstName(DEFAULT_FIRSTNAME);
        managedUserVM.setLastName(DEFAULT_LASTNAME);
        managedUserVM.setEmail(DEFAULT_EMAIL);// this email should already be used
        managedUserVM.setActivated(true);
        managedUserVM.setImageUrl(DEFAULT_IMAGEURL);
        managedUserVM.setLangKey(DEFAULT_LANGKEY);
        managedUserVM.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        // Create the User
        mockMvc.perform(post("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(managedUserVM)))
            .andExpect(status().isBadRequest());

        // Validate the User in the database
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    @WithMockUser(authorities = AuthoritiesConstants.USER)
    void getAllUsers() throws Exception {
        // Initialize the database
        user.setAuthorities(Collections.singleton(authorityRepository.findById(AuthoritiesConstants.USER).orElseThrow()));
        userRepository.saveAndFlush(user);

        // Get all the users
        mockMvc.perform(get("/api/users?sort=id,desc")
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].login").value(hasItem(DEFAULT_LOGIN)))
            .andExpect(jsonPath("$.[*].firstName").value(hasItem(DEFAULT_FIRSTNAME)))
            .andExpect(jsonPath("$.[*].lastName").value(hasItem(DEFAULT_LASTNAME)))
            .andExpect(jsonPath("$.[*].email").value(hasItem(DEFAULT_EMAIL)))
            .andExpect(jsonPath("$.[*].imageUrl").value(hasItem(DEFAULT_IMAGEURL)))
            .andExpect(jsonPath("$.[*].langKey").value(hasItem(DEFAULT_LANGKEY)))
            .andExpect(jsonPath("$.[*].authorities").exists())
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem("user")))
            .andExpect(jsonPath("$.[*].createdDate").exists())
            .andExpect(jsonPath("$.[*].lastModifiedBy").value(hasItem("user")))
            .andExpect(jsonPath("$.[*].lastModifiedDate").exists());
    }

    @Test
    @Transactional
    @WithMockUser(authorities = AuthoritiesConstants.USER)
    void getAllUsersIncludesPaginationLinks() throws Exception {
        userRepository.saveAndFlush(user);
        User anotherUser = createEntity(em);
        anotherUser.setLogin("paginationuser");
        anotherUser.setEmail("paginationuser@localhost");
        userRepository.saveAndFlush(anotherUser);

        mockMvc.perform(get("/api/users")
            .param("page", "0")
            .param("size", "1")
            .param("sort", "id,asc"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(header().string("Link", containsString("page=1")))
            .andExpect(header().string("Link", containsString("rel=\"next\"")))
            .andExpect(header().string("Link", containsString("rel=\"last\"")))
            .andExpect(header().string("Link", containsString("rel=\"first\"")))
            .andExpect(header().string("Link", containsString("size=1")));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = AuthoritiesConstants.USER)
    void getAllUsersReturnsStableLinksForEmptyPage() throws Exception {
        userRepository.deleteAll();
        userRepository.flush();

        mockMvc.perform(get("/api/users")
            .param("page", "0")
            .param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty())
            .andExpect(header().string("X-Total-Count", "0"))
            .andExpect(header().string("Link", containsString("page=0")))
            .andExpect(header().string("Link", containsString("rel=\"last\"")))
            .andExpect(header().string("Link", containsString("rel=\"first\"")))
            .andExpect(header().string("Link", not(containsString("rel=\"next\""))));
    }

    @Test
    @Transactional
    @WithMockUser(authorities = AuthoritiesConstants.USER)
    void getUser() throws Exception {
        // Initialize the database
        user.setAuthorities(Collections.singleton(authorityRepository.findById(AuthoritiesConstants.USER).orElseThrow()));
        userRepository.saveAndFlush(user);

        assertThat(cacheManager.getCache(UserRepository.USERS_BY_LOGIN_CACHE).get(user.getLogin())).isNull();

        // Get the user
        mockMvc.perform(get("/api/users/{login}", user.getLogin()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.login").value(user.getLogin()))
            .andExpect(jsonPath("$.firstName").value(DEFAULT_FIRSTNAME))
            .andExpect(jsonPath("$.lastName").value(DEFAULT_LASTNAME))
            .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
            .andExpect(jsonPath("$.imageUrl").value(DEFAULT_IMAGEURL))
            .andExpect(jsonPath("$.langKey").value(DEFAULT_LANGKEY))
            .andExpect(jsonPath("$.activated").value(true))
            .andExpect(jsonPath("$.authorities").value(hasItem(AuthoritiesConstants.USER)))
            .andExpect(jsonPath("$.createdBy").value("user"))
            .andExpect(jsonPath("$.createdDate").exists())
            .andExpect(jsonPath("$.lastModifiedBy").value("user"))
            .andExpect(jsonPath("$.lastModifiedDate").exists());

        assertThat(cacheManager.getCache(UserRepository.USERS_BY_LOGIN_CACHE).get(user.getLogin())).isNotNull();
    }

    @Test
    @Transactional
    @WithMockUser(authorities = AuthoritiesConstants.USER)
    void getNonExistingUser() throws Exception {
        mockMvc.perform(get("/api/users/unknown"))
            .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    @WithAnonymousUser
    void getAllUsersAsAnonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    @WithMockUser(authorities = AuthoritiesConstants.USER)
    void createUserAsUserIsForbidden() throws Exception {
        ManagedUserVm managedUserVM = new ManagedUserVm();
        managedUserVM.setLogin(DEFAULT_LOGIN);
        managedUserVM.setPassword(DEFAULT_PASSWORD);
        managedUserVM.setFirstName(DEFAULT_FIRSTNAME);
        managedUserVM.setLastName(DEFAULT_LASTNAME);
        managedUserVM.setEmail(DEFAULT_EMAIL);
        managedUserVM.setActivated(true);
        managedUserVM.setImageUrl(DEFAULT_IMAGEURL);
        managedUserVM.setLangKey(DEFAULT_LANGKEY);
        managedUserVM.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        mockMvc.perform(post("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(managedUserVM)))
            .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    @WithMockUser(authorities = AuthoritiesConstants.USER)
    void updateUserAsUserIsForbidden() throws Exception {
        ManagedUserVm managedUserVM = new ManagedUserVm();
        managedUserVM.setId(1L);
        managedUserVM.setLogin(DEFAULT_LOGIN);
        managedUserVM.setPassword(UPDATED_PASSWORD);
        managedUserVM.setFirstName(UPDATED_FIRSTNAME);
        managedUserVM.setLastName(UPDATED_LASTNAME);
        managedUserVM.setEmail(UPDATED_EMAIL);
        managedUserVM.setActivated(true);
        managedUserVM.setImageUrl(UPDATED_IMAGEURL);
        managedUserVM.setLangKey(UPDATED_LANGKEY);
        managedUserVM.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        mockMvc.perform(put("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(managedUserVM)))
            .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    @WithMockUser(authorities = AuthoritiesConstants.USER)
    void deleteUserAsUserIsForbidden() throws Exception {
        mockMvc.perform(delete("/api/users/{login}", DEFAULT_LOGIN))
            .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    @WithMockUser(authorities = AuthoritiesConstants.USER)
    void getAuthoritiesAsUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/users/authorities"))
            .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void updateUser() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);
        int databaseSizeBeforeUpdate = userRepository.findAll().size();

        // Update the user
        User updatedUser = userRepository.findById(user.getId()).get();

        ManagedUserVm managedUserVM = new ManagedUserVm();
        managedUserVM.setId(updatedUser.getId());
        managedUserVM.setLogin(updatedUser.getLogin());
        managedUserVM.setPassword(UPDATED_PASSWORD);
        managedUserVM.setFirstName(UPDATED_FIRSTNAME);
        managedUserVM.setLastName(UPDATED_LASTNAME);
        managedUserVM.setEmail(UPDATED_EMAIL);
        managedUserVM.setActivated(updatedUser.isActivated());
        managedUserVM.setImageUrl(UPDATED_IMAGEURL);
        managedUserVM.setLangKey(UPDATED_LANGKEY);
        managedUserVM.setCreatedBy(updatedUser.getCreatedBy());
        managedUserVM.setCreatedDate(updatedUser.getCreatedDate());
        managedUserVM.setLastModifiedBy(updatedUser.getLastModifiedBy());
        managedUserVM.setLastModifiedDate(updatedUser.getLastModifiedDate());
        managedUserVM.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        mockMvc.perform(put("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(managedUserVM)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-carcare-alert", "userManagement.updated"))
            .andExpect(header().string("X-carcare-params", updatedUser.getLogin()))
            .andExpect(jsonPath("$.id").value(updatedUser.getId()))
            .andExpect(jsonPath("$.login").value(updatedUser.getLogin()))
            .andExpect(jsonPath("$.firstName").value(UPDATED_FIRSTNAME))
            .andExpect(jsonPath("$.lastName").value(UPDATED_LASTNAME))
            .andExpect(jsonPath("$.email").value(UPDATED_EMAIL))
            .andExpect(jsonPath("$.imageUrl").value(UPDATED_IMAGEURL))
            .andExpect(jsonPath("$.activated").value(updatedUser.isActivated()))
            .andExpect(jsonPath("$.langKey").value(UPDATED_LANGKEY))
            .andExpect(jsonPath("$.createdBy").value(updatedUser.getCreatedBy()))
            .andExpect(jsonPath("$.createdDate").exists())
            .andExpect(jsonPath("$.lastModifiedBy").value(updatedUser.getLastModifiedBy()))
            .andExpect(jsonPath("$.lastModifiedDate").exists())
            .andExpect(jsonPath("$.authorities").value(hasItem(AuthoritiesConstants.USER)));

        // Validate the User in the database
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeUpdate);
        User testUser = userList.get(userList.size() - 1);
        assertThat(testUser.getFirstName()).isEqualTo(UPDATED_FIRSTNAME);
        assertThat(testUser.getLastName()).isEqualTo(UPDATED_LASTNAME);
        assertThat(testUser.getEmail()).isEqualTo(UPDATED_EMAIL);
        assertThat(testUser.getImageUrl()).isEqualTo(UPDATED_IMAGEURL);
        assertThat(testUser.getLangKey()).isEqualTo(UPDATED_LANGKEY);
    }

    @Test
    @Transactional
    void updateUserLogin() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);
        int databaseSizeBeforeUpdate = userRepository.findAll().size();

        // Update the user
        User updatedUser = userRepository.findById(user.getId()).get();

        ManagedUserVm managedUserVM = new ManagedUserVm();
        managedUserVM.setId(updatedUser.getId());
        managedUserVM.setLogin(UPDATED_LOGIN);
        managedUserVM.setPassword(UPDATED_PASSWORD);
        managedUserVM.setFirstName(UPDATED_FIRSTNAME);
        managedUserVM.setLastName(UPDATED_LASTNAME);
        managedUserVM.setEmail(UPDATED_EMAIL);
        managedUserVM.setActivated(updatedUser.isActivated());
        managedUserVM.setImageUrl(UPDATED_IMAGEURL);
        managedUserVM.setLangKey(UPDATED_LANGKEY);
        managedUserVM.setCreatedBy(updatedUser.getCreatedBy());
        managedUserVM.setCreatedDate(updatedUser.getCreatedDate());
        managedUserVM.setLastModifiedBy(updatedUser.getLastModifiedBy());
        managedUserVM.setLastModifiedDate(updatedUser.getLastModifiedDate());
        managedUserVM.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        mockMvc.perform(put("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(managedUserVM)))
            .andExpect(status().isOk());

        // Validate the User in the database
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeUpdate);
        User testUser = userList.get(userList.size() - 1);
        assertThat(testUser.getLogin()).isEqualTo(UPDATED_LOGIN);
        assertThat(testUser.getFirstName()).isEqualTo(UPDATED_FIRSTNAME);
        assertThat(testUser.getLastName()).isEqualTo(UPDATED_LASTNAME);
        assertThat(testUser.getEmail()).isEqualTo(UPDATED_EMAIL);
        assertThat(testUser.getImageUrl()).isEqualTo(UPDATED_IMAGEURL);
        assertThat(testUser.getLangKey()).isEqualTo(UPDATED_LANGKEY);
    }

    @Test
    @Transactional
    void updateUserExistingEmail() throws Exception {
        // Initialize the database with 2 users
        userRepository.saveAndFlush(user);

        User anotherUser = new User();
        anotherUser.setLogin("jhipster");
        anotherUser.setPassword(RandomStringUtils.random(60));
        anotherUser.setActivated(true);
        anotherUser.setEmail("jhipster@localhost");
        anotherUser.setFirstName("java");
        anotherUser.setLastName("hipster");
        anotherUser.setImageUrl("");
        anotherUser.setLangKey("en");
        userRepository.saveAndFlush(anotherUser);

        // Update the user
        User updatedUser = userRepository.findById(user.getId()).get();

        ManagedUserVm managedUserVM = new ManagedUserVm();
        managedUserVM.setId(updatedUser.getId());
        managedUserVM.setLogin(updatedUser.getLogin());
        managedUserVM.setPassword(updatedUser.getPassword());
        managedUserVM.setFirstName(updatedUser.getFirstName());
        managedUserVM.setLastName(updatedUser.getLastName());
        managedUserVM.setEmail("jhipster@localhost");// this email should already be used by anotherUser
        managedUserVM.setActivated(updatedUser.isActivated());
        managedUserVM.setImageUrl(updatedUser.getImageUrl());
        managedUserVM.setLangKey(updatedUser.getLangKey());
        managedUserVM.setCreatedBy(updatedUser.getCreatedBy());
        managedUserVM.setCreatedDate(updatedUser.getCreatedDate());
        managedUserVM.setLastModifiedBy(updatedUser.getLastModifiedBy());
        managedUserVM.setLastModifiedDate(updatedUser.getLastModifiedDate());
        managedUserVM.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        mockMvc.perform(put("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(managedUserVM)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void updateUserExistingLogin() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);

        User anotherUser = new User();
        anotherUser.setLogin("jhipster");
        anotherUser.setPassword(RandomStringUtils.random(60));
        anotherUser.setActivated(true);
        anotherUser.setEmail("jhipster@localhost");
        anotherUser.setFirstName("java");
        anotherUser.setLastName("hipster");
        anotherUser.setImageUrl("");
        anotherUser.setLangKey("en");
        userRepository.saveAndFlush(anotherUser);

        // Update the user
        User updatedUser = userRepository.findById(user.getId()).get();

        ManagedUserVm managedUserVM = new ManagedUserVm();
        managedUserVM.setId(updatedUser.getId());
        managedUserVM.setLogin("jhipster");// this login should already be used by anotherUser
        managedUserVM.setPassword(updatedUser.getPassword());
        managedUserVM.setFirstName(updatedUser.getFirstName());
        managedUserVM.setLastName(updatedUser.getLastName());
        managedUserVM.setEmail(updatedUser.getEmail());
        managedUserVM.setActivated(updatedUser.isActivated());
        managedUserVM.setImageUrl(updatedUser.getImageUrl());
        managedUserVM.setLangKey(updatedUser.getLangKey());
        managedUserVM.setCreatedBy(updatedUser.getCreatedBy());
        managedUserVM.setCreatedDate(updatedUser.getCreatedDate());
        managedUserVM.setLastModifiedBy(updatedUser.getLastModifiedBy());
        managedUserVM.setLastModifiedDate(updatedUser.getLastModifiedDate());
        managedUserVM.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        mockMvc.perform(put("/api/users")
            .contentType(TestUtil.APPLICATION_JSON_UTF8)
            .content(TestUtil.convertObjectToJsonBytes(managedUserVM)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @Transactional
    void deleteUser() throws Exception {
        // Initialize the database
        userRepository.saveAndFlush(user);
        int databaseSizeBeforeDelete = userRepository.findAll().size();

        // Delete the user
        mockMvc.perform(delete("/api/users/{login}", user.getLogin())
            .accept(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isNoContent())
            .andExpect(header().string("X-carcare-alert", "userManagement.deleted"))
            .andExpect(header().string("X-carcare-params", user.getLogin()));

        assertThat(cacheManager.getCache(UserRepository.USERS_BY_LOGIN_CACHE).get(user.getLogin())).isNull();

        // Validate the database is empty
        List<User> userList = userRepository.findAll();
        assertThat(userList).hasSize(databaseSizeBeforeDelete - 1);
    }

    @Test
    @Transactional
    void deleteNonExistingUserStillReturnsDeletionContract() throws Exception {
        mockMvc.perform(delete("/api/users/{login}", "missinguser"))
            .andExpect(status().isNoContent())
            .andExpect(header().string("X-carcare-alert", "userManagement.deleted"))
            .andExpect(header().string("X-carcare-params", "missinguser"));
    }

    @Test
    @Transactional
    void getAllAuthorities() throws Exception {
        mockMvc.perform(get("/api/users/authorities")
            .accept(TestUtil.APPLICATION_JSON_UTF8)
            .contentType(TestUtil.APPLICATION_JSON_UTF8))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").value(hasItems(AuthoritiesConstants.USER, AuthoritiesConstants.ADMIN)));
    }

    @Test
    @Transactional
    void testUserEquals() throws Exception {
        TestUtil.equalsVerifier(User.class);
        User user1 = new User();
        user1.setId(1L);
        User user2 = new User();
        user2.setId(user1.getId());
        assertThat(user1).isEqualTo(user2);
        user2.setId(2L);
        assertThat(user1).isNotEqualTo(user2);
        user1.setId(null);
        assertThat(user1).isNotEqualTo(user2);
    }

    @Test
    void testUserDtotoUser() {
        UserDto userDto = new UserDto();
        userDto.setId(DEFAULT_ID);
        userDto.setLogin(DEFAULT_LOGIN);
        userDto.setFirstName(DEFAULT_FIRSTNAME);
        userDto.setLastName(DEFAULT_LASTNAME);
        userDto.setEmail(DEFAULT_EMAIL);
        userDto.setActivated(true);
        userDto.setImageUrl(DEFAULT_IMAGEURL);
        userDto.setLangKey(DEFAULT_LANGKEY);
        userDto.setCreatedBy(DEFAULT_LOGIN);
        userDto.setLastModifiedBy(DEFAULT_LOGIN);
        userDto.setAuthorities(Collections.singleton(AuthoritiesConstants.USER));

        User user = userMapper.userDtoToUser(userDto);
        assertThat(user.getId()).isEqualTo(DEFAULT_ID);
        assertThat(user.getLogin()).isEqualTo(DEFAULT_LOGIN);
        assertThat(user.getFirstName()).isEqualTo(DEFAULT_FIRSTNAME);
        assertThat(user.getLastName()).isEqualTo(DEFAULT_LASTNAME);
        assertThat(user.getEmail()).isEqualTo(DEFAULT_EMAIL);
        assertThat(user.isActivated()).isEqualTo(true);
        assertThat(user.getImageUrl()).isEqualTo(DEFAULT_IMAGEURL);
        assertThat(user.getLangKey()).isEqualTo(DEFAULT_LANGKEY);
        assertThat(user.getCreatedBy()).isNull();
        assertThat(user.getCreatedDate()).isNotNull();
        assertThat(user.getLastModifiedBy()).isNull();
        assertThat(user.getLastModifiedDate()).isNotNull();
        assertThat(user.getAuthorities()).extracting("name").containsExactly(AuthoritiesConstants.USER);
    }

    @Test
    void testUserToUserDto() {
        user.setId(DEFAULT_ID);
        user.setCreatedBy(DEFAULT_LOGIN);
        user.setCreatedDate(Instant.now());
        user.setLastModifiedBy(DEFAULT_LOGIN);
        user.setLastModifiedDate(Instant.now());
        Set<Authority> authorities = new HashSet<>();
        Authority authority = new Authority();
        authority.setName(AuthoritiesConstants.USER);
        authorities.add(authority);
        user.setAuthorities(authorities);

        UserDto userDto = userMapper.userToUserDto(user);

        assertThat(userDto.getId()).isEqualTo(DEFAULT_ID);
        assertThat(userDto.getLogin()).isEqualTo(DEFAULT_LOGIN);
        assertThat(userDto.getFirstName()).isEqualTo(DEFAULT_FIRSTNAME);
        assertThat(userDto.getLastName()).isEqualTo(DEFAULT_LASTNAME);
        assertThat(userDto.getEmail()).isEqualTo(DEFAULT_EMAIL);
        assertThat(userDto.isActivated()).isEqualTo(true);
        assertThat(userDto.getImageUrl()).isEqualTo(DEFAULT_IMAGEURL);
        assertThat(userDto.getLangKey()).isEqualTo(DEFAULT_LANGKEY);
        assertThat(userDto.getCreatedBy()).isEqualTo(DEFAULT_LOGIN);
        assertThat(userDto.getCreatedDate()).isEqualTo(user.getCreatedDate());
        assertThat(userDto.getLastModifiedBy()).isEqualTo(DEFAULT_LOGIN);
        assertThat(userDto.getLastModifiedDate()).isEqualTo(user.getLastModifiedDate());
        assertThat(userDto.getAuthorities()).containsExactly(AuthoritiesConstants.USER);
        assertThat(userDto.toString()).isNotNull();
    }

    @Test
    void testAuthorityEquals() {
        Authority authorityA = new Authority();
        assertThat(authorityA).isEqualTo(authorityA);
        assertThat(authorityA).isNotEqualTo(null);
        assertThat(authorityA).isNotEqualTo(new Object());
        // Two nameless (unmapped) Authority instances must hash the same, per equals/hashCode's
        // own contract; the concrete value is Lombok's, not a meaningful constant to hardcode.
        assertThat(authorityA.hashCode()).isEqualTo(new Authority().hashCode());
        assertThat(authorityA.toString()).isNotNull();

        Authority authorityB = new Authority();
        assertThat(authorityA).isEqualTo(authorityB);

        authorityB.setName(AuthoritiesConstants.ADMIN);
        assertThat(authorityA).isNotEqualTo(authorityB);

        authorityA.setName(AuthoritiesConstants.USER);
        assertThat(authorityA).isNotEqualTo(authorityB);

        authorityB.setName(AuthoritiesConstants.USER);
        assertThat(authorityA).isEqualTo(authorityB);
        assertThat(authorityA.hashCode()).isEqualTo(authorityB.hashCode());
    }
}
