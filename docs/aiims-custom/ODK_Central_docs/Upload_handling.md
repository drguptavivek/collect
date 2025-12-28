# ODK Central auth and roles (current model):

  Auth mechanisms

  - Web users (type user): Email + password (bcrypt). Login creates a session (token/csrf) stored in sessions;
    used via cookie or Authorization: Bearer <token> for APIs.
  - Field keys / App Users (type field_key): Central creates a long-lived session token, distributed via QR. The
    token is sent in the URL (/key/<token> or ?st=); no password.
  - Public links (type public_link): Also tokens in URL for webforms; limited verb set.
  - Basic auth: Allowed when OIDC is off, over HTTPS; for user only. Not for field_key/public_link.
  - OIDC: Can be enabled; disables Basic.

  Auth handler code: server/lib/http/preprocessors.js (authHandler) branches: fieldKey token → session lookup
  (field_key/public_link only); Bearer → session lookup; Basic → bcrypt check (user); Cookie → session lookup
  + CSRF.

  Roles and verbs

  - admins: Full access to all projects/actors. System role.
  - app-user (field_key): For App Users; limited to form download/submit, no admin UI.
  - manager: Project-level role with broad rights on that project.
  - form manager: Can manage forms on the project.
  - data collector/enumerator equivalents: Can download forms and submit data; verb sets include form.read,
    submission.create.
  - viewer/project viewer: Read-only access to forms/submissions.
  - public_link: System role for public web submissions.
  - Roles are stored in roles; grants are in grants linking role→verb; assignments are in assignments linking
    actor→role→actee (project/form/dataset). Verbs include project.*, user.*, form.*, submission.*, dataset.*,
    etc.
  - Submission checks: submission.create required on the form/project for new submissions; submission.update/
    delete/restore/read/list for other endpoints.

  Submission endpoints: In resources/submissions.js, after parsing multipart/XML, Central looks up the form, calls
  auth.canOrReject('submission.create', form). Auth object comes from the handler above.

  Key takeaways

  - Tokens in Central are sessions; field_key/public_link tokens are long-lived, user sessions are time-limited.
  - Roles are verb-based; per-project assignments control who can submit/download.



# ODK COllect
 - Manual send flow lives in 
 `collect_app/src/main/java/org/odk/collect/android/instancemanagement/send/InstanceUploaderActivity.java`
    it spins up InstanceUploaderTask with selected instance
    IDs, optional override URL/credentials/delete
    flag from intent, shows progress, and prompts
    for auth if a 401 comes back. The task reports
    per-instance messages and clears any temporary
    credentials afterward.

  - Upload execution is in
    InstanceUploaderTask.doInBackground → InstanceServerUploader \
    (collect_app/src/main/java/org/odk/collect/android/upload/InstanceServerUploader.java). 
    
    For each finalized instance (sorted by finalization date) it:
      - Marks the record as SUBMISSION_FAILED up
        front.    - Chooses the destination URL in
        getUrlToSubmitTo (override URL → instance
        submission URI → project server URL) and
        appends deviceID.
      - Issues a HEAD request first to validate
        OpenRosa compliance, read X-OpenRosa-Accept-Content-Length, 
        and follow redirects only to
        the same host (else it fails). A 401 throws
        FormUploadAuthRequestedException (so manual
        flow can retry after credentials).

      - Picks submission.xml if present (handles
        encryption window), otherwise the instance
        XML, ensures it exists, and uploads it plus
        all non-hidden sibling files.

      - Treats only 201/202 as success; 200/401/400/
        other codes become FormUploadException,
        using any OpenRosa response message when
        available. On success the instance is
        marked SUBMITTED and any server message
        is returned.

  - Auto-send path is handled by
    InstancesDataService.sendInstances
    (collect_app/src/main/java/org/odk/collect/android/instancemanagement/InstancesDataService.kt)
    using InstanceSubmitter (InstanceSubmitter.kt).

    It grabs complete or failed instances filtered
    by InstanceAutoSendFetcher (respects form
    auto-send mode; can be forced-only when
    triggered by form metadata), uploads via
    InstanceServerUploader, logs analytics, and
    returns a per-instance exception map. 
    
    Results go to Notifier for user feedback.

  - Post-send cleanup: InstanceAutoDeleteChecker
    (collect_app/src/main/java/org/odk/collect/android/utilities/InstanceAutoDeleteChecker.kt)
    deletes successfully sent instances when project
    “delete after send” is on unless the form opts
    out, or when the form explicitly opts in even
    if the project setting is off. 
    
    Manual task InstanceUploaderTask also deletes on success
    using the same checker when a custom delete flag
    is set.

  - Auto-send scheduling:
    FormUpdateAndInstanceSubmitScheduler
    (collect_app/src/main/java/org/odk/collect/android/backgroundwork/FormUpdateAndInstanceSubmitScheduler.java)
    schedules SendFormsTaskSpec workers with network
    constraints derived from project auto-send
    settings (Wi‑Fi, cellular, both).
    Form-level forced auto-send uses a separate tag with no
    network constraint. 
    
    SendFormsTaskSpec retries with exponential backoff (up to 13 retries).
    AutoSendSettingsProvider gates UI/logic on current network vs settings.

  - Status/bookkeeping: instance statuses transition
    COMPLETE/SUBMISSION_FAILED → SUBMISSION_FAILED during attempt → SUBMITTED on success; 
    Failures keep their message so the UI can show per-instance results. 
    
    Temporary credentials from intents are saved/cleared around each run.

## Credential lookup: 

WebCredentialsUtils 
(collect_app/src/main/java/org/odk/collect/android/utilities/WebCredentialsUtils.java) 
    chooses credentials per host. It first checks an in-memory map populated by intent
    extras (saveCredentials in InstanceUploaderTask / InstanceUploaderActivity); if the host matches the current
    project server URL, it falls back to saved prefs (ProjectKeys.KEY_USERNAME/KEY_PASSWORD) when no temp creds
    exist. Hosts not matching prefs get either the temp creds (if set) or blank user/pass. Temp creds are cleared
    after the task.
  - HTTP client/auth: InstanceServerUploader passes those credentials to OpenRosaHttpInterface (OkHttp-backed).
    The provider (open-rosa/http/okhttp/OkHttpOpenRosaServerClientProvider.java) builds per-scheme/per-credential
    clients with Basic and Digest authenticators (burgstaller library) plus an auth cache interceptor, sends
    OpenRosa headers (User-Agent, X-OpenRosa-Version, Date), and trusts Let’s Encrypt root on older Android. Auth
    challenges are handled by OkHttp authenticators; a 401 on HEAD bubbles up as FormUploadAuthRequestedException
    to trigger the credential prompt in manual flow.
  - Multipart construction: OkHttpConnection.uploadSubmissionAndFiles 
    (open-rosa/http/okhttp/OkHttpConnection.java) builds a multipart/form-data POST:
      - Part xml_submission_file is the main XML (submissionFile).
      - Each media file in the instance directory is added as form-data with name and filename equal to the file
        name; content type is resolved via CollectThenSystemContentTypeMapper (adds Collect-specific audio/video
        types, otherwise MimeTypeMap, fallback application/octet-stream).
      - It enforces the server’s X-OpenRosa-Accept-Content-Length: batches files until size or count thresholds
        are hit, then sends multiple requests, flagging intermediate ones with *isIncomplete*=yes. Only 201/202
        responses are treated as success; otherwise the first failing HttpPostResult is returned.
  - Other HTTP behaviors: HEAD is issued first to validate OpenRosa, follow same-host redirects, and read X-
    OpenRosa-Accept-Content-Length. GET/POST/HEAD all go through the same client factory and credentials path.


## CENTRAL-Server 

entral generates the credential for App Users automatically. When you create an App User (field key) there is
  no user-chosen password; the backend creates a long-lived session token:

  - Creation: POST /projects/:projectId/app-users → FieldKeys.create → Sessions.create with a random token and
    expiry far in the future (server/lib/model/query/field-keys.js and server/lib/model/query/sessions.js).
  - Auth path: Requests that include /key/<token>/... or ?st=<token> are parsed by fieldKeyParser (server/
    lib/http/middleware.js), and authHandler treats that token as a Field Key session (server/lib/http/
    preprocessors.js). Only actors of type field_key (and public_link) are accepted; no username/password is
    involved.
  - How Collect uses it: The settings QR Central shows for an App User encodes the server URL with the /key/
    <token> prefix (passwordless). Collect just hits that URL; the token in the path is the credential. The
    username/password fields in the QR are empty for App Users.

  So the “credential” you didn’t type is the session token Central generated and embedded in the QR’s server URL.

   - Central generates an App User by creating a long‑lived session token (“field key”). There is no human
    password. The token is the credential.
  - The managed QR code encodes general.server_url with the /key/<token>/ prefix (username/password left empty).
    When Collect uses that server URL, every request carries the token in the path (or as ?st=<token>), and
    Central’s auth handler recognizes it as a field key session.
  - Field key creation: POST /projects/:projectId/app-users → FieldKeys.create → Sessions.create (token with
    distant expiry). Auth handling for tokens is in server/lib/http/preprocessors.js and URL parsing in server/
    lib/http/middleware.js.
  - Managed QR codes also set form sync/autosend options so forms are pulled/pushed automatically for that token.
    The token itself should be protected like a password; anyone with the QR can upload as that App User.

• For an App User QR from Central, Collect’s general.server_url is set to include the field‑key token in the path,
  e.g.:

  - Server URL in settings (what the QR encodes):
    https://central.example.org/v1/key/<field-key-token>/projects/7
  - The submission endpoint Collect will POST to (it appends /submission):
    https://central.example.org/v1/key/<field-key-token>/projects/7/submission

  No username/password are set; the token in the path is the credential.

   OpenRosa submission endpoints (server/lib/resources/submissions.js):
      - POST multipart must include xml_submission_file plus attachment files whose names match binary field
        names. Multer only captures file parts; text parts (like a token field) are ignored. Unknown/extra files
        are not used and will fail validation if they don’t match expected binary fields.
      - Only query param consumed is deviceID; the rest of the multipart isn’t read beyond XML and attachments.
      - Auth is via the normal Central auth chain (auth.canOrReject('submission.create', form)) using project
        credentials/session. There is one special case: draft testing can use a URL token (/test/:key/.../draft/
        submission checks form.def.draftToken), but that’s URL-based, not a multipart field.
  - Standard REST submission endpoints behave similarly: XML body is parsed, optional deviceID query param, no
    extra fields; auth via project permissions.

  So: you cannot send an arbitrary token as an extra multipart field and have Central use it. To pass a token
  today, you’d need to use an existing auth mechanism (Basic/Digest/session) or the draft URL token path. Adding a
  new token channel (header/query/field) would require code changes in resources/submissions.js (and possibly auth
  middleware) to read/validate it.

  Want me to sketch the changes to accept a Authorization: Bearer <token> header or a multipart token field and
  hook it into auth?