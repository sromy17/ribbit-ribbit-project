const fs=require('node:fs');
// Instrument configuration from the same versioned catalog shown by the frontend.
// Never writes transaction records or changes existing instrument status/identity.
module.exports=async function configureCatalog(pool){
 const catalog=JSON.parse(fs.readFileSync('frontend/public/data/us-v1.json'));
 const client=await pool.connect();
 try{
  await client.query('BEGIN');
  await client.query('LOCK TABLE instruments IN SHARE ROW EXCLUSIVE MODE');
  for(const item of catalog.symbols.filter(x=>x.type==='equity'&&x.currency==='USD')){
   const existing=await client.query('SELECT instrumentid FROM instruments WHERE UPPER(ticker)=UPPER($1)',[item.symbol]);
   // A ticker shared by several instruments needs explicit venue mapping.
   if(existing.rowCount>1)continue;
   if(!existing.rowCount)await client.query("INSERT INTO instruments(instrumentid,ticker,instrumentname,status) SELECT COALESCE(MAX(instrumentid),0)+1,$1,$2,'ACTIVE' FROM instruments",[item.symbol,item.name]);
   await client.query("UPDATE instruments SET asset_class=COALESCE(asset_class,'EQUITY'), market=COALESCE(market,'US'), quantity_unit=COALESCE(quantity_unit,'SHARE'), quote_currency=COALESCE(quote_currency,'USD'), settlement_currency=COALESCE(settlement_currency,'USD'), contract_multiplier=COALESCE(contract_multiplier,1), product_convention=COALESCE(product_convention,'PRICE_PER_UNIT'), metadata_source=COALESCE(metadata_source,$2) WHERE UPPER(ticker)=UPPER($1)",[item.symbol,catalog.id]);
  }
  await client.query('COMMIT');
 }catch(e){await client.query('ROLLBACK');throw e;}finally{client.release();}
};
