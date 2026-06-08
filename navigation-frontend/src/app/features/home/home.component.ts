import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  currentYear: number;
  isLoading = false;
  animatedText = '';
  fullText = 'Navigation Aérienne';
  textIndex = 0;

  constructor(private router: Router) {
    this.currentYear = new Date().getFullYear();
  }

  ngOnInit(): void {
    this.typeWriterEffect();
  }

  typeWriterEffect(): void {
    if (this.textIndex < this.fullText.length) {
      this.animatedText += this.fullText.charAt(this.textIndex);
      this.textIndex++;
      setTimeout(() => this.typeWriterEffect(), 150);
    }
  }

  async navigateToLogin(): Promise<void> {
    this.isLoading = true;
    await new Promise(resolve => setTimeout(resolve, 800));
    this.router.navigate(['/login']);
  }

  navigateToFeatures(): void {
    document.getElementById('features')?.scrollIntoView({ behavior: 'smooth' });
  }
}
