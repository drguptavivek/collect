
# Types of users in ODK
Today, there are two types of accounts: Users, which are the administrative accounts held by staff members managing the data collection process, and App Users, which are restricted access keys granted per Form within a Project to data collection clients in the field. Although both of these entities are backed by Actors as we explain in the Authentication section above, there is not yet any way to directly create or manipulate an Actor. Today, you can only create, manage, and delete Users and App Users.

## Web Users

## App Users
App Users may only be created, fetched, and manipulated within the nested Projects subresource, as App Users themselves are limited to the Project in which they are created. Through the App Users API, you can create, list, and delete the App Users of any given Project. Because they have extremely limited permissions, App Users cannot manage themselves; only Users may access this API.

For more information about the /projects containing resource, please see the following section.

- Listing all App Users
GET /v1/projects/{projectId}/app-users

Currently, there are no paging or filtering options, so listing App Users will get you every App User in the system, every time.

This endpoint supports retrieving extended metadata; provide a header X-Extended-Metadata: true to additionally retrieve the lastUsed timestamp of each App User, as well as to retrieve the details of the Actor the App User was createdBy.

Parameters
projectId   number  The numeric ID of the Project
Example: 7
HTTP Status: 200

Content Type: application/json
Example
[
  {
    "createdAt": "2018-04-18T23:19:14.802Z",
    "displayName": "My Display Name",
    "id": 115,
    "type": "user",
    "updatedAt": "2018-04-18T23:42:11.406Z",
    "deletedAt": "2018-04-18T23:42:11.406Z",
    "token": "d1!E2GVHgpr4h9bpxxtqUJ7EVJ1Q$Dusm2RBXg8XyVJMCBCbvyE8cGacxUx3bcUT",
    "projectId": 1,
    "lastUsed": "2018-04-14T08:34:21.633Z"
  }
]

Schema
HTTP Status: 200
Content Type: application/json; extended
Example
[
  {
    "createdAt": "2018-04-18T23:19:14.802Z",
    "displayName": "My Display Name",
    "id": 115,
    "type": "user",
    "updatedAt": "2018-04-18T23:42:11.406Z",
    "deletedAt": "2018-04-18T23:42:11.406Z",
    "token": "d1!E2GVHgpr4h9bpxxtqUJ7EVJ1Q$Dusm2RBXg8XyVJMCBCbvyE8cGacxUx3bcUT",
    "projectId": 1,
    "createdBy": {
      "createdAt": "2018-04-18T23:19:14.802Z",
      "displayName": "My Display Name",
      "id": 115,
      "type": "user",
      "updatedAt": "2018-04-18T23:42:11.406Z",
      "deletedAt": "2018-04-18T23:42:11.406Z"
    },
    "lastUsed": "2018-04-14T08:34:21.633Z"
  }
]



### Creating a new App User
POST /v1/projects/{projectId}/app-users

The only information required to create a new App User is its displayName (this is called "Nickname" in the administrative panel).

When an App User is created, they are assigned no rights. They will be able to authenticate and list forms on a mobile client, but the form list will be empty, as the list only includes Forms that the App User has read access to. Once an App User is created, you'll likely wish to use the Form Assignments resource to actually assign the app-user role to them for the Forms you wish.

Parameters

projectId
number
The numeric ID of the Project
Example: 7
Request body


Example
{
  "displayName": "My Display Name"
}


Schema
HTTP Status: 200

Content Type: application/json


Example
{
  "createdAt": "2018-04-18T23:19:14.802Z",
  "displayName": "My Display Name",
  "id": 115,
  "type": "user",
  "updatedAt": "2018-04-18T23:42:11.406Z",
  "deletedAt": "2018-04-18T23:42:11.406Z",
  "token": "d1!E2GVHgpr4h9bpxxtqUJ7EVJ1Q$Dusm2RBXg8XyVJMCBCbvyE8cGacxUx3bcUT",
  "projectId": 1
}


Schema
HTTP Status: 400

Content Type: application/json


Example
{
  "code": "400",
  "message": "Could not parse the given data (2 chars) as json."
}


Schema
###  Deleting an App User
DELETE /v1/projects/{projectId}/app-users/{id}

You don't have to delete a App User in order to cut off its access. Using a User's credentials you can simply log the App User's session out using its token. This will end its session without actually deleting the App User, which allows you to still see it in the configuration panel and inspect its history. This is what the administrative panel does when you choose to "Revoke" the App User.

That said, if you do wish to delete the App User altogether, you can do so by issuing a DELETE request to its resource path. App Users cannot delete themselves.

Parameters

id
number
The numeric ID of the App User
Example: 16
projectId
number
The numeric ID of the Project
Example: 7
HTTP Status: 200

Content Type: application/json


Example
{
  "success": true
}

# App User Authentication
App Users are only allowed to list and download forms, and upload new submissions to those forms. Primarily, this is to allow clients like ODK Collect to use the OpenRosa API (/formList and /submission), but any action in this API reference falling into those categories will be allowed.

Revoking an App User is same as deleting session token. You can do this by calling DELETE /sessions/{appUser}.

## Using App User Authentication
GET /v1/key/{appUser}/example3

To use App User Authentication, first obtain an App User, typically by using the configuration panel in the user interface, or else by using the App User API Resource. Once you have the token, you can apply it to any eligible action by prefixing the URL with /key/{appUser} as follows:

/v1/key/!Ms7V3$Zdnd63j5HFacIPFEvFAuwNqTUZW$AsVOmaQFf$vIC!F8dJjdgiDnJXXOt/example/request/path

(There is not really anything at /v1/example3; this section only demonstrates how generally to use App User Authentication.)

#### Request Parameters 

appUser:  string  - The App User token. As with Session Bearer tokens, these tokens only contain URL-safe characters, so no escaping is required.
Example: !Ms7V3$Zdnd63j5HFacIPFEvFAuwNqTUZW$AsVOmaQFf$vIC!F8dJjdgiDnJXXOt

#### Example Response
HTTP Status: 200
Content Type: application/json

Example
{
  "success": true
}



