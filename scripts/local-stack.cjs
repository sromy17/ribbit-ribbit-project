#!/usr/bin/env node
/** Persistent local application. Never drops a database or loads demonstration fixtures. */
const fs=require('node:fs'),path=require('node:path'),{spawn,spawnSync}=require('node:child_process');
const {Pool}=require('../shared/auth-stub/node_modules/pg');
const dotenv=require('../shared/auth-stub/node_modules/dotenv');
const root=path.resolve(__dirname,'..');process.chdir(root);
const runtime=path.join(root,'.local');fs.mkdirSync(runtime,{recursive:true,mode:0o700});
const envPath=path.join(root,'.env');
if(!fs.existsSync(envPath)){
 const legacy=fs.existsSync('shared/auth-stub/.env')?dotenv.parse(fs.readFileSync('shared/auth-stub/.env')):{};
 const source=legacy.DB_OLTP_URL||legacy.DB_URL||'jdbc:postgresql://127.0.0.1:5432/ribbit_oltp';
 const target=source.includes('oltp')?source.replace(/oltp/g,'olap'):'jdbc:postgresql://127.0.0.1:5432/ribbit_olap';
 const user=legacy.DB_OLTP_USER||legacy.DB_USER||'postgres';const password=legacy.DB_OLTP_PASSWORD||legacy.DB_PASSWORD||'';
 const values={...legacy,DB_URL:source,DB_OLTP_URL:source,DB_OLAP_URL:target,DB_USER:user,DB_PASSWORD:password,DB_OLTP_USER:user,DB_OLTP_PASSWORD:password,DB_OLAP_USER:user,DB_OLAP_PASSWORD:password,AUTH_STUB_PORT:'4000',SERVER_PORT:'8081',FRONTEND_PORT:'4200',JWT_SHARED_SECRET:legacy.JWT_SHARED_SECRET||require('node:crypto').randomBytes(48).toString('base64url'),ORDER_EXECUTION_KAFKA_ENABLED:'false',MARKET_DATA_KAFKA_ENABLED:'false',ETL_ENABLED:'true',ETL_FIXED_DELAY_MS:'10000',SPRING_PROFILES_ACTIVE:'local'};
 fs.writeFileSync(envPath,Object.entries(values).map(([k,v])=>`${k}=${JSON.stringify(v)}`).join('\n')+'\n',{mode:0o600});
 console.log('Created .env using the existing local auth/database configuration. Secrets were not printed.');
}
const config={...dotenv.parse(fs.readFileSync(envPath)),...process.env};
function dbConfig(url,user,password){const u=new URL(url.replace(/^jdbc:/,''));return {host:u.hostname,port:Number(u.port)||5432,database:decodeURIComponent(u.pathname.slice(1)),user:user||decodeURIComponent(u.username),password:password||decodeURIComponent(u.password),connectionTimeoutMillis:5000};}
const source=dbConfig(config.DB_OLTP_URL||config.DB_URL,config.DB_OLTP_USER||config.DB_USER,config.DB_OLTP_PASSWORD||config.DB_PASSWORD);
const target=dbConfig(config.DB_OLAP_URL,config.DB_OLAP_USER||config.DB_USER,config.DB_OLAP_PASSWORD||config.DB_PASSWORD);
function quote(name){return '"'+name.replaceAll('"','""')+'"';}
async function prepare(){
 if(source.host===target.host&&source.port===target.port&&source.database===target.database)throw new Error('OLTP and OLAP must be different databases');
 const admin=new Pool({...source,database:'postgres'});
 try{for(const name of [source.database,target.database]){if(!(await admin.query('SELECT 1 FROM pg_database WHERE datname=$1',[name])).rowCount){await admin.query('CREATE DATABASE '+quote(name));console.log('Created database '+name);}}}finally{await admin.end();}
 const oltp=new Pool(source),olap=new Pool(target);
 try{
  const tables=await oltp.query("SELECT COUNT(*)::int AS count FROM information_schema.tables WHERE table_schema='public'");
  if(tables.rows[0].count===0){await oltp.query(fs.readFileSync('basedSchema.sql','utf8'));console.log('Initialized empty OLTP schema (no demo accounts).');}
  await oltp.query(fs.readFileSync('migrations/2026-10-23-auth-column.sql','utf8'));
  await oltp.query(fs.readFileSync('migrations/2026-10-26-local-compatibility.sql','utf8'));
  const schema=fs.readFileSync('basedSchema.sql','utf8').replace(/^\s*DROP\s+TABLE[^;]*;/gim,'').replace(/CREATE\s+TABLE\s+(?!IF NOT EXISTS)/gi,'CREATE TABLE IF NOT EXISTS ');
  await oltp.query(schema);
  await require('./local-instrument-catalog.cjs')(oltp);
  const report=fs.readFileSync('olap.sql','utf8').replace(/^\s*DROP\s+TABLE[^;]*;/gim,'').replace(/CREATE\s+TABLE\s+(?!IF NOT EXISTS)/gi,'CREATE TABLE IF NOT EXISTS ');
  await olap.query(report);
  for(const table of ['instruments_info','trade_facts','holdings_info']) {
   await olap.query('ALTER TABLE '+quote(table)+' ALTER COLUMN instrumentname TYPE VARCHAR(255)');
  }
  await olap.query('ALTER TABLE fill_facts ALTER COLUMN fee DROP NOT NULL');
  console.log(`Persistent source: ${source.host}:${source.port}/${source.database}`);
  console.log(`Persistent reports: ${target.host}:${target.port}/${target.database}`);
 }finally{await oltp.end();await olap.end();}
}
function child(command,args,name,extra={}){const log=fs.openSync(path.join(runtime,name+'.log'),'a',0o600);const proc=spawn(command,args,{cwd:root,env:{...config,...extra},stdio:['ignore',log,log]});proc.on('error',e=>console.error(name+': '+e.message));return proc;}
async function wait(url,proc){for(let i=0;i<90;i++){if(proc.exitCode!==null)throw new Error('Service exited; see .local logs');try{const r=await fetch(url,{signal:AbortSignal.timeout(1000)});if(r.ok)return;}catch{}await new Promise(r=>setTimeout(r,1000));}throw new Error('Startup timed out: '+url);}
async function main(){
 if(process.argv.includes('--load-only')){console.log('Configuration loaded; values omitted.');return;}
 await prepare();if(process.argv.includes('--prepare'))return;
 const ports=[Number(config.SERVER_PORT||8081),Number(config.AUTH_STUB_PORT||4000),Number(config.FRONTEND_PORT||4200)];
 for(const port of ports){await new Promise((resolve,reject)=>{const s=require('node:net').createServer();s.once('error',()=>reject(new Error(`Port ${port} is already in use. Stop that service before starting this stack.`)));s.listen(port,'127.0.0.1',()=>s.close(resolve));});}
 console.log('Building backend...');const build=spawnSync('mvn',['-q','package','-DskipTests'],{cwd:root,env:config,stdio:['ignore',fs.openSync(path.join(runtime,'build.log'),'w'),fs.openSync(path.join(runtime,'build-error.log'),'w')]});if(build.status!==0)throw new Error('Backend build failed; see .local/build logs');
 const children=[];let stopping=false;
 const stop=()=>{if(stopping)return;stopping=true;children.forEach(p=>{if(p.exitCode===null)p.kill('SIGTERM');});};
 process.on('SIGINT',()=>{stop();process.exitCode=0;});process.on('SIGTERM',()=>{stop();process.exitCode=0;});process.on('exit',stop);
 try{
  const backend=child('java',['-jar','target/team-skeleton.jar'],'backend',{SERVER_PORT:String(ports[0]),ORDER_EXECUTION_KAFKA_ENABLED:config.ORDER_EXECUTION_KAFKA_ENABLED||'false'});children.push(backend);await wait(`http://127.0.0.1:${ports[0]}/actuator/health`,backend);
  const auth=child(process.execPath,['shared/auth-stub/server.js'],'auth',{AUTH_STUB_PORT:String(ports[1])});children.push(auth);await wait(`http://127.0.0.1:${ports[1]}/health`,auth);
  const proxy=JSON.parse(fs.readFileSync('frontend/proxy.conf.json'));for(const [key,entry]of Object.entries(proxy))entry.target=`http://127.0.0.1:${key==='/auth'?ports[1]:ports[0]}`;
  fs.writeFileSync(path.join(runtime,'proxy.json'),JSON.stringify(proxy));
  const frontend=child(process.execPath,['frontend/node_modules/@angular/cli/bin/ng.js','serve','--host','127.0.0.1','--port',String(ports[2]),'--proxy-config',path.join(runtime,'proxy.json')],'frontend',{NG_BUILD_MAX_WORKERS:'2'});children.push(frontend);await wait(`http://127.0.0.1:${ports[2]}`,frontend);
  fs.writeFileSync(path.join(runtime,'services.json'),JSON.stringify({launcher:process.pid,backend:backend.pid,auth:auth.pid,frontend:frontend.pid,ui:`http://127.0.0.1:${ports[2]}`,source:source.database,reporting:target.database}),{mode:0o600});
  console.log(`READY: http://127.0.0.1:${ports[2]} — persistent local application. Ctrl-C stops services, databases remain.`);
  await Promise.race(children.map(proc=>new Promise(resolve=>proc.once('exit',resolve))));
 }finally{stop();}
}
main().catch(e=>{console.error('Local startup failed: '+e.message);process.exitCode=1;});
