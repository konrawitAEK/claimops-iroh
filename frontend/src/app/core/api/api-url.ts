import { environment } from '../../../environments/environment';

/** Base URL of the claims REST API; the identity interceptor only adds headers to calls under it. */
export const API_URL: string = environment.apiUrl;
