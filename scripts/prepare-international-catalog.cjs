// Adds the international equities exposed by the UI without altering existing identity/status.
const entries = [
  ['HSBA.L', 'HSBC Holdings', 'UK', 'LSE', 'GBP'],
  ['SHEL.L', 'Shell', 'UK', 'LSE', 'GBP'],
  ['BP.L', 'BP', 'UK', 'LSE', 'GBP'],
  ['LLOY.L', 'Lloyds Banking Group', 'UK', 'LSE', 'GBP'],
  ['VOD.L', 'Vodafone Group', 'UK', 'LSE', 'GBP'],
  ['AZN.L', 'AstraZeneca', 'UK', 'LSE', 'GBP'],
  ['RELIANCE.NS', 'Reliance Industries', 'IN', 'NSE', 'INR'],
  ['TCS.NS', 'Tata Consultancy Services', 'IN', 'NSE', 'INR'],
  ['INFY.NS', 'Infosys', 'IN', 'NSE', 'INR'],
  ['HDFCBANK.NS', 'HDFC Bank', 'IN', 'NSE', 'INR']
];
module.exports = async function prepareInternationalCatalog(pool) {
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    await client.query('LOCK TABLE instruments IN SHARE ROW EXCLUSIVE MODE');
    let added = 0;
    for (const [ticker, name, market, venue, currency] of entries) {
      const existing = await client.query('SELECT instrumentid FROM instruments WHERE upper(ticker)=upper($1)', [ticker]);
      if (existing.rowCount > 1) throw new Error(`Ambiguous ticker ${ticker}; no changes applied`);
      if (existing.rowCount) continue;
      await client.query(`INSERT INTO instruments
        (instrumentid,ticker,instrumentname,status,asset_class,market,venue,quote_currency,
         settlement_currency,quantity_unit,contract_multiplier,product_convention,metadata_source,market_data_symbol)
        SELECT coalesce(max(instrumentid),0)+1,$1,$2,'ACTIVE','EQUITY',$3,$4,$5,$5,'SHARE',1,
               'PRICE_PER_UNIT','ribbit-global-v2',$1 FROM instruments`, [ticker,name,market,venue,currency]);
      added++;
    }
    await client.query('COMMIT');
    return { added, totalInternationalSymbols: entries.length };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally { client.release(); }
};
