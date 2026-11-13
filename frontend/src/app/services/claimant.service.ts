import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import { API_URL } from '../core/api';
import type { ClaimDetail, ClaimSummary, SubmitClaimRequest } from '../models';

/** Claimant API calls. The backend scopes every call to the claimant in the identity headers. */
@Injectable({ providedIn: 'root' })
export class ClaimantService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/claimant/claims`;

  submit(request: SubmitClaimRequest): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(this.base, request);
  }

  myClaims(): Observable<ClaimSummary[]> {
    return this.http.get<ClaimSummary[]>(this.base);
  }

  byNumber(claimNumber: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(`${this.base}/by-number/${encodeURIComponent(claimNumber)}`);
  }

  reply(claimId: string, answer: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/${claimId}/info-responses`, { answer });
  }
}
