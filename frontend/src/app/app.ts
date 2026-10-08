import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ApiError, ApiService } from './api.service';
import { AppInfo } from './models';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
})
export class App implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly info = signal<AppInfo | null>(null);
  protected readonly infoError = signal<string | null>(null);

  ngOnInit(): void {
    this.api.info().subscribe({
      next: (i) => this.info.set(i),
      error: (e: ApiError) => this.infoError.set(e.message),
    });
  }
}
