// Explicit local bootstrap: never changes existing users or their permissions.
const fs=require('node:fs'),crypto=require('node:crypto');
const {Pool}=require('../shared/auth-stub/node_modules/pg');
const bcrypt=require('../shared/auth-stub/node_modules/bcryptjs');
const env=require('../shared/auth-stub/node_modules/dotenv').parse(fs.readFileSync('.env'));
const url=new URL((env.DB_OLTP_URL||env.DB_URL).replace(/^jdbc:/,''));
const pool=new Pool({host:url.hostname,port:Number(url.port)||5432,database:url.pathname.slice(1),user:env.DB_OLTP_USER||env.DB_USER,password:env.DB_OLTP_PASSWORD||env.DB_PASSWORD});
(async()=>{
 const client=await pool.connect();
 try {
  await client.query('BEGIN');
  if(process.argv.includes('--legacy-usd')) await client.query("UPDATE accounts SET currency='USD' WHERE currency IS NULL");
  const created=[];
  for(const [username,role] of [['local_admin','PLATFORM_ADMIN'],['local_analyst','ANALYST'],['local_operations','OPERATIONS'],['local_trader','USER']]){
   if((await client.query('SELECT 1 FROM users WHERE username=$1',[username])).rowCount)continue;
   const password=crypto.randomBytes(18).toString('base64url');
   const id=(await client.query('SELECT COALESCE(MAX(userid),0)+1 AS id FROM users')).rows[0].id;
   await client.query("INSERT INTO users(userid,username,salt,userhashedsaltedpassword,first_name,last_name,registered_at_utc) VALUES($1,$2,'bcrypt',$3,'Local',$4,NOW())",[id,username,await bcrypt.hash(password,10),role]);
   await client.query('INSERT INTO roles(rolename) VALUES($1) ON CONFLICT DO NOTHING',[role]);
   await client.query('INSERT INTO user_roles(userid,roleid) SELECT $1,roleid FROM roles WHERE rolename=$2',[id,role]);
   created.push(`${username} (${role})\nPassword: ${password}\n`);
  }
  await client.query('COMMIT');
  fs.mkdirSync('.local',{recursive:true,mode:0o700});
  if(created.length)fs.appendFileSync('.local/staff-credentials.txt',created.join('\n'),{mode:0o600});
  console.log(`Created ${created.length} separate local accounts. Credentials: .local/staff-credentials.txt. Existing users unchanged.`);
 }catch(e){await client.query('ROLLBACK');throw e;}finally{client.release();await pool.end();}
})().catch(e=>{console.error(e.message);process.exitCode=1;});
