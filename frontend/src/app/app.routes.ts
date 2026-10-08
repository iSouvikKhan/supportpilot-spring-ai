import { Routes } from '@angular/router';
import { ApprovalsPage } from './approvals/approvals-page';
import { ChatPage } from './chat/chat-page';

export const routes: Routes = [
  { path: '', component: ChatPage, title: 'SupportPilot · Customer chat' },
  { path: 'approvals', component: ApprovalsPage, title: 'SupportPilot · Approvals' },
  { path: '**', redirectTo: '' },
];
