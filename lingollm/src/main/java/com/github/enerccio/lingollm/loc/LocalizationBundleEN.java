package com.github.enerccio.lingollm.loc;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class LocalizationBundleEN extends LocalizationBundleBase {

    @Override
    public Language getLanguage() {
        return Language.EN;
    }

    @Override
    public String getDisplayName() {
        return "English";
    }

    @Override
    protected void loadMessages() {
        // --- LABEL ---
        setValue(L.LABEL_YES, "Yes");
        setValue(L.LABEL_NO, "No");
        setValue(L.LABEL_EXIT, "Exit");
        setValue(L.LABEL_USERNAME, "Username");
        setValue(L.LABEL_LOGIN, "Login");
        setValue(L.LABEL_LOGOUT, "Logout");
        setValue(L.LABEL_USER_FULLNAME, "Full Name");
        setValue(L.LABEL_CURRENT_PASSWORD, "Current Password");
        setValue(L.LABEL_PASSWORD, "Password");
        setValue(L.LABEL_PASSWORD_AGAIN, "Repeat password");
        setValue(L.LABEL_FORGOT_PASSWORD, "Forgotten password?");
        setValue(L.LABEL_SAVE_LOGIN, "Save login");
        setValue(L.LABEL_AUTHENTICATION_FAILED, "Failed to authenticate");
        setValue(L.LABEL_APPLICATION_ERROR, "Application Error");
        setValue(L.LABEL_SAVE, "Save");
        setValue(L.LABEL_OK, "OK");
        setValue(L.LABEL_CANCEL, "Cancel");
        setValue(L.LABEL_MODELS, "Inference Providers");
        setValue(L.LABEL_ADD_MODEL, "Add Inference Provider");
        setValue(L.LABEL_TYPE, "Type");
        setValue(L.LABEL_NAME, "Name");
        setValue(L.LABEL_ADMIN, "Admin");
        setValue(L.LABEL_EDIT_AI, "Edit Inference Provider");
        setValue(L.LABEL_NEW_AI, "New Inference Provider");
        setValue(L.LABEL_URL, "OpenAI URL (ends with /v1)");
        setValue(L.LABEL_API_KEY, "Api Key");
        setValue(L.LABEL_RESET, "Reset Api Key");
        setValue(L.LABEL_MODEL, "Model");
        setValue(L.LABEL_REFRESH, "Refresh");
        setValue(L.LABEL_MAX_CONTEXT, "Max Context Size");
        setValue(L.LABEL_MAX_RESPONSE, "Max Response Tokens");
        setValue(L.LABEL_ADDITIONAL_PARAMETERS, "Additional Parameters");
        setValue(L.LABEL_ADMINISTRATOR, "Administrator");
        setValue(L.LABEL_GENERAL_SETTINGS, "General Settings");
        setValue(L.LABEL_TYPE_SETTINGS, "Per Type Settings");
        setValue(L.LABEL_JAILBREAK, "Jailbreak Prompt");
        setValue(L.LABEL_NEEDS_JAILBREAK, "Needs Jailbreak");
        setValue(L.LABEL_ENABLED_REASONING, "Enable Reasoning");
        setValue(L.LABEL_REASONING_EFFORT, "Reasoning Effort");
        setValue(L.LABEL_SETTINGS, "Settings");
        setValue(L.LABEL_DEFAULT_MODEL, "Default Model");
        setValue(L.LABEL_NOTE, "Note");
        setValue(L.LABEL_DELETE, "Delete");
        setValue(L.LABEL_STOP, "Stop");
        setValue(L.LABEL_EXTENSIONS, "Extensions");
        setValue(L.LABEL_UPLOAD_EXTENSION, "Load Extension (.jar)");
        setValue(L.LABEL_UNLOAD_EXTENSION, "Unload");
        setValue(L.LABEL_BUNDLE_ID, "ID");
        setValue(L.LABEL_BUNDLE_NAME, "Extension Name");
        setValue(L.LABEL_BUNDLE_VERSION, "Version");
        setValue(L.LABEL_BUNDLE_STATE, "State");
        setValue(L.LABEL_ACTIONS, "Actions");
        setValue(L.LABEL_EXTENSION_SETTINGS, "Extension Settings");
        setValue(L.LABEL_CREATED, "Created");
        setValue(L.LABEL_USERS, "Users");
        setValue(L.LABEL_ADD_USER, "Add User");
        setValue(L.LABEL_EDIT_USER, "Edit User");
        setValue(L.LABEL_NEW_USER, "New User");
        setValue(L.LABEL_CHANGE_PASSWORD, "Change Password");
        setValue(L.LABEL_DATABASE_BACKUPS, "Database Backups");
        setValue(L.LABEL_CREATE_BACKUP, "Create Backup");
        setValue(L.LABEL_UPLOAD_BACKUP, "Upload Backup");
        setValue(L.LABEL_DOWNLOAD, "Download");
        setValue(L.LABEL_SIZE, "Size");
        setValue(L.LABEL_DATABASE_SIZE, "Current database size: %s");
        setValue(L.LABEL_RESTORE_ON_RESTART, "Restore on restart");
        setValue(L.LABEL_CANCEL_RESTORE, "Cancel Restore");
        setValue(L.LABEL_SCHEDULED_BACKUPS, "Scheduled Backups");
        setValue(L.LABEL_BACKUP_SCHEDULE_ENABLED, "Create backups automatically");
        setValue(L.LABEL_BACKUP_SCHEDULE, "Schedule (cron)");
        setValue(L.LABEL_BACKUP_KEEP, "Keep scheduled backups");
        setValue(L.LABEL_SAVE_SCHEDULE, "Save Schedule");
        setValue(L.LABEL_NEVER, "never");
        setValue(L.LABEL_BACKUP_MANUAL, "Manual");
        setValue(L.LABEL_BACKUP_SCHEDULED, "Scheduled");
        setValue(L.LABEL_CLEANUP, "Cleanup");
        setValue(L.LABEL_ANALYZE, "Analyze");
        setValue(L.LABEL_RUN_CLEANUP, "Run Cleanup");
        setValue(L.LABEL_ENTITY, "Entity");
        setValue(L.LABEL_SOFT_DELETED, "Deleted");
        setValue(L.LABEL_PURGEABLE, "To purge");
        setValue(L.LABEL_BLOCKED, "Blocked");
        setValue(L.LABEL_BLOCKED_ENTITIES, "Deleted objects still in use");
        setValue(L.LABEL_REFERENCED_BY, "Referenced by");
        setValue(L.LABEL_REFERENCE_MODEL, "Reference Model");
        setValue(L.LABEL_REFERRER, "Referrer");
        setValue(L.LABEL_FIELD, "Field");
        setValue(L.LABEL_TARGET, "Target");
        setValue(L.LABEL_POLICY, "Policy");
        setValue(L.LABEL_CLEAR_PASSWORD, "Clear password");
        setValue(L.LABEL_DISCARD_CHANGES, "Discard Changes");
        setValue(L.LABEL_UNLOCK_ACCOUNT, "Unlock account (too many failed logins)");
        setValue(L.LABEL_LANGUAGE, "Language");
        setValue(L.LABEL_FIT_TEXT_AREA_TO_CONTENT, "Grow to fit the whole text");
        setValue(L.LABEL_FIXED_TEXT_AREA_HEIGHT, "Fixed height, scroll the text");
        setValue(L.LABEL_CLOSE, "Close");
        setValue(L.LABEL_RESOURCES, "Resources");
        setValue(L.LABEL_LINKED_OBJECT, "Used by");
        setValue(L.LABEL_REPLACE_CONTENT, "Replace content");
        setValue(L.LABEL_DELETE_SELECTED, "Delete selected");
        setValue(L.LABEL_REQUEST_TIMEOUT, "Timeout (seconds)");
        setValue(L.LABEL_MAX_RETRIES, "Retries");
        setValue(L.LABEL_TEST_CONNECTION, "Test Connection");
        setValue(L.LABEL_TRASH, "Trash");
        setValue(L.LABEL_RESTORE, "Restore");
        setValue(L.LABEL_RESTORE_SELECTED, "Restore Selected");
        setValue(L.LABEL_USER, "User");
        setValue(L.LABEL_ALL_USERS, "All users");
        setValue(L.LABEL_ALL_TYPES, "All types");
        setValue(L.LABEL_EXTENDED_CONTENT, "Extended Content");
        setValue(L.LABEL_BUNDLE_VERIFICATION, "Verification");
        setValue(L.LABEL_VERIFICATION_REPORT, "Verification Report");
        setValue(L.LABEL_VALID, "Valid");
        setValue(L.LABEL_INVALID, "Invalid");
        setValue(L.LABEL_APP_DESC, "Language learning app with LLM");
        setValue(L.LABEL_START, "Start");
        setValue(L.LABEL_LOG, "Log");
        setValue(L.LABEL_CLEAR, "Clear");
        setValue(L.LABEL_SHARED, "Shared");
        setValue(L.LABEL_SHARED_WITH_ALL_USERS, "Shared with all users");
        setValue(L.LABEL_USERS_CAN_CREATE_AIS, "Users can create their own models");
        setValue(L.LABEL_TOKENS_SENT, "Tokens sent");
        setValue(L.LABEL_TOKENS_GENERATED, "Tokens generated");
        setValue(L.LABEL_USAGE_PER_USER, "Usage per user");
        setValue(L.LABEL_REQUESTS, "Requests");
        setValue(L.LABEL_LAST_USED, "Last used");
        setValue(L.LABEL_LIMITS, "Limits");
        setValue(L.LABEL_LIMIT, "Limit");
        setValue(L.LABEL_ADD_LIMIT, "Add limit");
        setValue(L.LABEL_EDIT_LIMIT, "Edit limit");
        setValue(L.LABEL_YOUR_LIMITS, "Your limits");
        setValue(L.LABEL_LIMIT_TYPE, "Limit type");
        setValue(L.LABEL_RESET_PERIOD, "Reset");
        setValue(L.LABEL_NEXT_RESET, "Next reset");
        setValue(L.LABEL_LIMIT_TOKENS_SENT, "Limit of tokens sent");
        setValue(L.LABEL_LIMIT_TOKENS_GENERATED, "Limit of tokens generated");
        setValue(L.LABEL_LIMIT_TOKENS_TOTAL, "Limit of tokens in total");
        setValue(L.LABEL_TOKENS_TOTAL, "Tokens in total");
        setValue(L.LABEL_UNLIMITED, "Unlimited");
        setValue(L.LABEL_USED, "Used");
        setValue(L.LABEL_LIMIT_HARD, "hard");
        setValue(L.LABEL_LIMIT_GENERATED_HARD, "Hard limit: refuse a request whose reserved response would go over");
        setValue(L.LABEL_DELETE_PERMANENTLY, "Delete Permanently");
        setValue(L.LABEL_SHA256, "SHA-256");
        setValue(L.LABEL_LEARNING, "Learning");
        setValue(L.LABEL_ADD_LANGUAGE, "Add language");
        setValue(L.LABEL_LANGUAGE_CREATOR_TITLE, "New language");
        setValue(L.LABEL_TEMPLATE, "Template");
        setValue(L.LABEL_PROFILE_NAME, "Profile name");
        setValue(L.LABEL_LANGUAGE_TEMPLATE, "Language");
        setValue(L.LABEL_LANGUAGE_PROFILE, "Language profile");
        setValue(L.LABEL_FREE_CHAT, "Free chat");


        // --- ENUM ---
        setValue(L.ENUM_AI_TYPE_OPEN_AI_COMPATIBLE, "OpenAI Compatible");
        setValue(L.ENUM_REASONING_NONE, "None");
        setValue(L.ENUM_REASONING_LOW, "Low");
        setValue(L.ENUM_REASONING_MEDIUM, "Medium");
        setValue(L.ENUM_REASONING_HIGH, "High");
        setValue(L.ENUM_CLEANUP_POLICY_STRONG, "Blocks purge");
        setValue(L.ENUM_CLEANUP_POLICY_OWNED_BY, "Purged with target");
        setValue(L.ENUM_CLEANUP_POLICY_OWNS, "Owns target");
        setValue(L.ENUM_CLEANUP_POLICY_WEAK, "Cleared on purge");
        setValue(L.ENUM_DOMAIN_MESSAGE, "Message");
        setValue(L.ENUM_DOMAIN_RESOURCE, "Resource");
        setValue(L.ENUM_DOMAIN_SETTING, "Setting");
        setValue(L.ENUM_BUNDLE_STATE_UNINSTALLED, "Uninstalled");
        setValue(L.ENUM_BUNDLE_STATE_INSTALLED, "Installed");
        setValue(L.ENUM_BUNDLE_STATE_RESOLVED, "Resolved");
        setValue(L.ENUM_BUNDLE_STATE_STARTING, "Starting");
        setValue(L.ENUM_BUNDLE_STATE_STOPPING, "Stopping");
        setValue(L.ENUM_BUNDLE_STATE_ACTIVE, "Active");
        setValue(L.ENUM_BUNDLE_STATE_UNKNOWN, "Unknown (%d)");
        setValue(L.ENUM_BUNDLE_STATE_STOPPED, "Stopped");
        setValue(L.ENUM_LIMIT_UNLIMITED, "Unlimited");
        setValue(L.ENUM_LIMIT_TOKEN_LIMIT, "Token limit");
        setValue(L.ENUM_LIMIT_NOT_AVAILABLE, "Not available");
        setValue(L.ENUM_RESET_DAILY, "Daily");
        setValue(L.ENUM_RESET_WEEKLY, "Weekly (from Monday)");
        setValue(L.ENUM_RESET_MONTHLY, "Monthly");
        setValue(L.ENUM_LANGUAGE_JAPANESE, "Japanese");
        setValue(L.ENUM_LANGUAGE_GERMAN, "German");


        // --- ERROR ---
        setValue(L.ERROR_INTERNAL_SERVER_ERROR, "Internal Server Error");
        setValue(L.ERROR_EXTENSION_INSTALL_FAILED, "Failed to load extension: %s");
        setValue(L.ERROR_EXTENSION_UNINSTALL_FAILED, "Failed to unload extension: %s");
        setValue(L.ERROR_INVALID_IMAGE, "The file is not a PNG, JPEG, GIF or WebP image, or it is larger than 10 MB.");
        setValue(L.ERROR_INFERENCE_DETAILS, "%s Provider says: %s");
        setValue(L.ERROR_INFERENCE_AUTHENTICATION, "The provider rejected the API key. Check the API key of the inference provider.");
        setValue(L.ERROR_INFERENCE_PERMISSION, "The API key is not allowed to use this model or endpoint.");
        setValue(L.ERROR_INFERENCE_MODEL_NOT_FOUND, "The provider does not know the configured model. Check the model of the inference provider.");
        setValue(L.ERROR_INFERENCE_NOT_FOUND, "Nothing was found at the configured URL. Check the URL of the inference provider (it usually ends with /v1).");
        setValue(L.ERROR_INFERENCE_CONTEXT_OVERFLOW, "The prompt does not fit into the context of the model. Lower Max Context Size or the response size, or use a model with a larger context.");
        setValue(L.ERROR_INFERENCE_RATE_LIMIT, "The provider is limiting requests (rate limit or quota). Wait a moment and try again.");
        setValue(L.ERROR_INFERENCE_SERVER, "The provider failed to process the request. Try again later.");
        setValue(L.ERROR_INFERENCE_TIMEOUT, "The provider did not answer in time. Try again or raise the timeout of the inference provider.");
        setValue(L.ERROR_INFERENCE_CONNECTION, "Cannot connect to the provider. Check the URL of the inference provider and your network.");
        setValue(L.ERROR_INFERENCE_BAD_REQUEST, "The provider rejected the request. Check the model and the additional parameters of the inference provider.");
        setValue(L.ERROR_INFERENCE_UNKNOWN, "The request to the provider failed.");
        setValue(L.ERROR_EXTENSION_INCOMPATIBLE, "Extension %s does not fit this version of LingoLLM and was not loaded.");
        setValue(L.ERROR_EXTENSION_START_FAILED, "Failed to start extension: %s");
        setValue(L.ERROR_EXTENSION_STOP_FAILED, "Failed to stop extension: %s");
        setValue(L.ERROR_INFERENCE_NOT_AVAILABLE, "This model is not available to you.");
        setValue(L.ERROR_INFERENCE_LIMIT_REACHED, "Your token limit for this model is used up, or this request would go over it. The Models tab shows your limits and when they reset.");
        setValue(L.ERROR_LIMIT_NEGATIVE, "Limits can not be negative.");
        setValue(L.ERROR_LIMIT_TOTAL_UNREACHABLE, "The limits of tokens sent and tokens generated add up to less than the limit in total, so the total would never be reached.");
        setValue(L.ERROR_LIMIT_USER_REQUIRED, "Choose a user.");

        // --- MSG ---
        setValue(L.MSG_VALIDATION_FAILED_CANT_SAVE, "Cannot save form.");
        setValue(L.MSG_VALIDATION_FAILED_CANT_SAVE_EXT, "Cannot save form. Invalid fields: ");
        setValue(L.MSG_AUTHENTICATION_FAILED_INFO, "Invalid credentials.");
        setValue(L.MSG_INVALID_JSON_OBJECT, "JSON Invalid.");
        setValue(L.MSG_FETCH_MODELS_FAILED, "Failed to download model list.");
        setValue(L.MSG_SETTINGS_SAVED, "Settings saved successfully.");
        setValue(L.MSG_CONFIRM_DELETE, "Are you sure you want to delete this item?");
        setValue(L.MSG_EXTENSION_INSTALLED_SUCCESS, "Extension loaded successfully.");
        setValue(L.MSG_EXTENSION_UNINSTALLED_SUCCESS, "Extension unloaded successfully.");
        setValue(L.MSG_CONFIRM_UNLOAD_EXTENSION, "Extension \"%s\" will be unloaded and its JAR deleted from the extensions folder. Continue?");
        setValue(L.MSG_LOGIN_ALREADY_EXISTS, "User with this login already exists.");
        setValue(L.MSG_PASSWORDS_DO_NOT_MATCH, "Passwords do not match.");
        setValue(L.MSG_CANNOT_DELETE_SELF, "You cannot delete your own account.");
        setValue(L.MSG_CANNOT_DELETE_LAST_ADMIN, "The last administrator cannot be deleted or demoted.");
        setValue(L.MSG_DATABASE_BACKUP_CREATED, "Database backup created.");
        setValue(L.MSG_CONFIRM_DATABASE_RESTORE, "Database will be replaced with backup \"%s\" on next application start. Current database will be kept in backup folder. Continue?");
        setValue(L.MSG_DATABASE_RESTORE_PENDING, "Database restore is scheduled. Restart the application to apply it.");
        setValue(L.MSG_INVALID_DATABASE_FILE, "The database can't be restored: %s.");
        setValue(L.MSG_BACKUP_SCHEDULE_NEXT, "Next backups: %s");
        setValue(L.MSG_BACKUP_SCHEDULE_INVALID, "Invalid schedule: %s");
        setValue(L.MSG_BACKUP_SCHEDULE_SAVED, "Backup schedule saved.");
        setValue(L.MSG_BACKUP_SCHEDULE_OFF, "Scheduled backups are off.");
        setValue(L.MSG_BACKUP_SCHEDULE_STATUS, "Last scheduled backup: %s. Next: %s.");
        setValue(L.MSG_ADMIN_ONLY, "Only administrators can do this.");
        setValue(L.MSG_CLEANUP_DESCRIPTION, "Cleanup permanently removes deleted objects that are no longer used anywhere, together with data that belongs only to them (messages, entries, tag relations...). Deleted objects that are still referenced are kept.");
        setValue(L.MSG_CONFIRM_CLEANUP, "%d objects will be permanently removed. Consider creating database backup first. Continue?");
        setValue(L.MSG_CLEANUP_DONE, "Cleanup removed %d objects.");
        setValue(L.MSG_NOTHING_TO_CLEANUP, "There is nothing to clean up.");
        setValue(L.MSG_AI_RESPONSE_EXCEEDS_CONTEXT, "Max Response Tokens must be lower than Max Context Size.");
        setValue(L.MSG_NO_PASSWORD_WARNING, "Warning! No password provided. If this is a server instance, please provide password for every user. Are you sure you want to save user with blank password?");
        setValue(L.MSG_CLEAR_PASSWORD_WARNING, "The password will be removed and the user's saved logins ended. The user then logs in with the user name only, anyone who knows it can log in until a new password is set. Continue?");
        setValue(L.MSG_UNSAVED_SETTINGS, "Unsaved changes - save or discard them to leave the tab.");
        setValue(L.MSG_RESOURCE_NOT_LINKED, "nothing");
        setValue(L.MSG_RESOURCE_LINK_MISSING, "%s (gone)");
        setValue(L.MSG_RESOURCE_REPLACED, "The content was replaced.");
        setValue(L.MSG_CONFIRM_DELETE_RESOURCES, "Delete %s selected resources? Whatever uses them will lose them. The files stay in the data folder.");
        setValue(L.MSG_RESOURCES_EMPTY, "There are no resources yet. Files you attach to your chats are listed here.");
        setValue(L.MSG_CONNECTION_OK, "Connection works: %s models available and the model answered.");
        setValue(L.MSG_CONNECTION_OK_NO_MODELS, "Connection works: the model answered (the provider does not list its models).");
        setValue(L.MSG_TRASH_DESCRIPTION, "Deleted objects stay here until an administrator runs the cleanup. Select what you want back and restore it. An object that belongs to a deleted object (a message of a deleted chat) can only be restored together with it.");
        setValue(L.MSG_TRASH_EMPTY, "Nothing is deleted.");
        setValue(L.MSG_CONFIRM_RESTORE, "Restore %s selected objects?");
        setValue(L.MSG_RESTORED, "%s objects were restored.");
        setValue(L.MSG_RESTORE_BLOCKED, "Nothing was restored. These objects need a deleted object that is not selected. Select it too, or leave them deleted.");
        setValue(L.MSG_RESTORE_BLOCKED_ITEM, "%s \"%s\" needs the deleted %s \"%s\" (%s)");
        setValue(L.MSG_NO_EXTENDED_CONTENT, "This object has no extended content.");
        setValue(L.MSG_EXTENSION_STARTED, "Extension started.");
        setValue(L.MSG_EXTENSION_STOPPED, "Extension stopped.");
        setValue(L.MSG_EXTENSION_LOG_EMPTY, "The extension has not written anything to its log.");
        setValue(L.MSG_DELETE_PERMANENTLY_HINT, "Removes the selected chat messages from the database for good. Other objects can only be restored; the cleanup removes them.");
        setValue(L.MSG_CONFIRM_DELETE_PERMANENTLY, "Permanently delete %s selected objects? This cannot be undone.");
        setValue(L.MSG_DELETED_PERMANENTLY, "%s objects were deleted permanently.");
        setValue(L.MSG_DELETE_PERMANENTLY_BLOCKED, "Nothing was deleted. These objects can't be removed:");
        setValue(L.MSG_DELETE_PERMANENTLY_BLOCKED_ITEM, "%s \"%s\": %s");
        setValue(L.MSG_COPIED_TO_CLIPBOARD, "Copied to clipboard");

        // --- HELP ---
        setValue(L.HELP_BACKUP_SCHEDULE, "minute hour day-of-month month day-of-week - e.g. \"0 3 * * *\" every day at 3:00, \"0 */6 * * *\" every 6 hours, \"30 2 * * 1\" Mondays at 2:30. @daily, @weekly and @monthly work too.");
        setValue(L.HELP_BACKUP_KEEP, "Older scheduled backups are deleted, 0 keeps all. Manual backups are never deleted.");
        setValue(L.HELP_AI_MAX_CONTEXT, "Total tokens the model accepts - prompt and response together.");
        setValue(L.HELP_AI_MAX_RESPONSE, "Longest response. This much of the context is kept free for the response.");
        setValue(L.HELP_AI_REQUEST_TIMEOUT, "How long to wait for the provider to answer or to send more text. Reasoning models may need minutes before the first text.");
        setValue(L.HELP_AI_MAX_RETRIES, "Retries of a request that failed with a rate limit (429), a server error (5xx) or a connection error. The wait between them grows.");
        setValue(L.HELP_LIMIT_EMPTY_UNLIMITED, "Leave a limit empty for no limit. Whichever limit is reached first stops the user until the next reset.");
        setValue(L.HELP_LIMIT_GENERATED_HARD, "A hard limit of the generated tokens refuses a request when the response it reserves (the maximum response size) would not fit into what is left; without it the last response can go over the limit.");

        // --- DESC ---

    }

    @Override
    public Locale getLocale() {
        return Locale.US;
    }

    @Override
    public DateFormat getDateFormat() {
        return new SimpleDateFormat("MM/dd/yyyy");
    }

    @Override
    public DateFormat getHourFormat() {
        return new SimpleDateFormat("HH:mm:ss");
    }

    @Override
    public DateFormat getDateHourFormat() {
        return new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    }
}
