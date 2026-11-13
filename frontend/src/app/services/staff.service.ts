import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import { API_URL } from '../core/api';
import type { AuditEntry, ClaimDetail, ClaimSummary, Dashboard, QueueFilter } from '../models';

/** Staff API calls: work queue, claim actions and the dashboard. */
@Injectable({ providedIn: 'root' })
export class StaffService {
  private readonly http = inject(HttpClient);
  private readonly base = `${API_URL}/staff`;

  queue(filter: QueueFilter): Observable<ClaimSummary[]> {
    let params = new HttpParams();
    if (filter.market) params = params.set('market', filter.market);
    if (filter.status) params = params.set('status', filter.status);
    if (filter.mine) params = params.set('assignedTo', 'me');
    return this.http.get<ClaimSummary[]>(`${this.base}/claims`, { params });
  }

  claim(id: string): Observable<ClaimDetail> {
    return this.http.get<ClaimDetail>(`${this.base}/claims/${id}`);
  }

  audit(id: string): Observable<AuditEntry[]> {
    return this.http.get<AuditEntry[]>(`${this.base}/claims/${id}/audit`);
  }

  pickUp(id: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/claims/${id}/pickup`, null);
  }

  requestInfo(id: string, question: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/claims/${id}/info-requests`, { question });
  }

  settle(id: string, settledAmount: number, note: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/claims/${id}/settlement`, { settledAmount, note });
  }

  reject(id: string, reason: string): Observable<ClaimDetail> {
    return this.http.post<ClaimDetail>(`${this.base}/claims/${id}/rejection`, { reason });
  }

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${this.base}/dashboard`);
  }
}
