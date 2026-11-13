import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { DEMO_USERS, MARKETS, homePath } from '../../core/labels';
import type { Market, Role } from '../../models';
import { SessionService } from '../../services/session.service';

/**
 * App shell: top bar with navigation and the role switcher.
 *
 * The switcher stands in for login in this demo. Changing role, name or market updates SessionService, whose identity
 * the HTTP interceptor sends on every API call, and a role change jumps to that role's home page (the route guards
 * would redirect there anyway).
 */
@Component({
  selector: 'app-layout',
  imports: [
    MatToolbarModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatSelectModule,
    MatIconModule,
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-layout.html',
  styleUrl: './app-layout.scss',
})
export class AppLayout {
  protected readonly session = inject(SessionService);
  private readonly router = inject(Router);
  protected readonly markets = MARKETS;

  protected users(): string[] {
    return DEMO_USERS[this.session.identity().role];
  }

  protected switchRole(role: Role): void {
    this.session.switchRole(role);
    void this.router.navigateByUrl(homePath(role));
  }

  protected setMarket(market: Market): void {
    this.session.setMarket(market);
  }
}
