import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-help',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './help.component.html',
  styleUrl: './help.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HelpComponent {
  readonly faqs = [
    {
      title: 'Order Execution & Buying Power',
      items: [
        { question: 'How are orders executed?', answer: 'Trade and Heatmap Quick Trade send orders to the backend. Displayed prices are estimates; the server determines execution price, fees, and whether an order is accepted. The estimated fee is 0.1% of notional, with a $1 minimum.' },
        { question: 'Why was my order rejected or left unconfirmed?', answer: 'Available cash, holdings, or trading rules may prevent execution. If confirmation is interrupted, check History before submitting again. Never assume a network error means the order did not execute.' }
      ]
    },
    {
      title: 'Portfolio Tracking & History Snapshots',
      items: [
        { question: 'What does the balance chart show?', answer: 'The chart shows recorded cash plus holdings valuations, not a simulated performance curve. History begins when this browser first observes the account. Earlier values are not invented.' },
        { question: 'When are snapshots recorded?', answer: 'Snapshots are captured after confirmed trades, valuation changes, and daily observations while the app is open or on the next refresh. No background snapshots are recorded when the browser is closed. Unavailable quotes may use cost basis with a valuation notice.' }
      ]
    },
    {
      title: 'Market Heatmap & TradingView Charts',
      items: [
        { question: 'What determines tile size and color?', answer: 'The heatmap displays up to 50 equities. Tile sizes currently use synthetic rank weights, not reported market capitalization. Color reflects daily percentage change on a scale clamped to -5% through +5%.' },
        { question: 'Is TradingView scraped or used for order pricing?', answer: 'No. The official TradingView widget renders its own chart in an iframe. Our app does not extract prices from it. Backend market data supplies heatmap and order prices; chart timing and coverage may differ.' },
        { question: 'Why do some news cards say Curated sample?', answer: 'Missing or rejected Finnhub credentials, timeouts, and other feed failures use locally curated sample content. These are not live publisher articles, and their links open searches. Editorial fallback images do not indicate live feed availability.' }
      ]
    },
    {
      title: 'Data Privacy & Local State Persistence',
      items: [
        { question: 'What is stored on this device?', answer: 'Account snapshots and cached market/news data use browser storage. Backend records remain authoritative for balances and orders. Clearing browser data removes local history; it does not undo server-side trades.' },
        { question: 'What should I do on a shared device?', answer: 'Log out when you finish. Browser storage can retain cached account data, so avoid using shared devices for sensitive activity.' }
      ]
    }
  ];

}
