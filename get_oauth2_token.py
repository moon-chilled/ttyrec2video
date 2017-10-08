from oauth2client.client import OAuth2WebServerFlow
from oauth2client.tools import run_flow
from oauth2client.file import Storage

def get_token(client_id, client_secret):
	flow = OAuth2WebServerFlow(client_id=client_id, client_secret=client_secret, scope='https://www.googleapis.com/auth/youtube.upload', redirect_uri='http://example.com/auth_return')
	storage = Storage('creds.data')

	credentials = run_flow(flow, storage)

	return credentials.access_token

if __name__ == "__main__":
	print(get_token(input("Please enter your client id: "), input("Please enter your client secret: ")))
