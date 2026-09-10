
import { writeFileSync } from "node:fs";
import { randomUUID } from "node:crypto";
import { parseArgs, isDeepStrictEqual } from "node:util";
import { artifactPath, definitions, sourceHash, scenarioHash, validateEvidence } from "./verification-common.mjs";

const { values } = parseArgs({ options: {
 api:{type:"string",default:"http://127.0.0.1:18081/api/v1"},web:{type:"string",default:"http://127.0.0.1:13000"},
 evidence:{type:"string",default:"artifacts/runtime/latest.json"},"verification-id":{type:"string",default:randomUUID()}
}});
const ownerName=process.env.VERIFY_OWNER_USERNAME??"owner@tradeops.test";
const ownerPassword=process.env.VERIFY_LOCAL_PASSWORD;
const password="Fixture-"+randomUUID(),nextPassword="Changed-"+randomUUID(),suffix=randomUUID().slice(0,8);
let current,report,output;
const state={owner:{},a:{},b:{},usernameA:"verify-a-"+suffix,usernameB:"verify-b-"+suffix};
function localUrl(value){const u=new URL(value);if(!["127.0.0.1","localhost","[::1]"].includes(u.hostname)||u.protocol!=="http:"||u.username||u.password||u.search||u.hash)throw Error("LOCAL_RUNTIME_REQUIRED");return u.toString().replace(/\/$/,"");}
function check(name,actual,expected=true){const passed=isDeepStrictEqual(actual,expected);current.checks.push({name,passed});if(!passed)throw Error("SCENARIO_ASSERTION_FAILED");}
async function request(path,{client={},method="GET",body,status=200,web=false,csrf=true,text=false}={}){
 const headers={};
 if(client.cookie)headers.Cookie=client.cookie;
 if(client.csrf&&csrf)headers["X-CSRF-TOKEN"]=client.csrf;
 if(body!==undefined){headers["Content-Type"]="application/json";body=JSON.stringify(body);}
 const response=await fetch((web?state.web:state.api)+path,{method,body,headers,redirect:"error",signal:AbortSignal.timeout(15000)});
 const cookie=response.headers.getSetCookie().find(v=>v.startsWith("SESSION="));
 if(cookie)client.cookie=cookie.split(";")[0];
 const cid=response.headers.get("X-Correlation-Id");
 if(current)current.requests.push({method,path:web?"/":path.split("?")[0].replace(/\d+(?=\/|$)/g,":id"),status:response.status,correlationId:web?null:cid});
 if(response.status!==status)throw Error("UNEXPECTED_HTTP_STATUS");
 if(!web&&!/^[A-Za-z0-9-]{8,64}$/.test(cid??""))throw Error("CORRELATION_ID_INVALID");
 const payload=await response.text();
 return {data:web||text?payload:payload?JSON.parse(payload):undefined,headers:response.headers};
}
async function csrf(client){client.csrf=(await request("/auth/csrf",{client})).data.token;}
async function login(client,username,secret){await csrf(client);return (await request("/auth/login",{client,method:"POST",body:{username,password:secret}})).data;}
const api=async(path,options={})=>(await request(path,{client:state.owner,...options})).data;
const query={q:"FICTIONAL VERIFICATION",mode:"HYBRID",page:0,size:20,saveHistory:true};
const handlers={
 "health-and-session":async()=>{
  const health=await api("/health");
  check("health-fields",health.status==="ok"&&Object.keys(health).sort().join(",")==="checkedAt,correlationId,service,status");
  check("anonymous-denied",(await request("/auth/me",{status:401})).data.code,"AUTHENTICATION_REQUIRED");
  check("csrf-required",(await request("/auth/login",{method:"POST",body:{username:"missing@tradeops.test",password:"invalid-pass"},status:403})).data.code,"CSRF_INVALID");
  check("owner-session",(await api("/auth/me")).owner,true);
 },
 "account-lifecycle":async()=>{
  for(const username of [state.usernameA,state.usernameB])await api("/users",{method:"POST",body:{username,password}});
  await login(state.a,state.usernameA,password);
  check("forced-password-change",(await api("/watchlist/sources",{client:state.a,status:403})).code,"PASSWORD_CHANGE_REQUIRED");
  await api("/account/password",{client:state.a,method:"POST",body:{currentPassword:password,newPassword:nextPassword}});
  check("old-session-revoked",(await api("/auth/me",{client:state.a,status:401})).code,"AUTHENTICATION_REQUIRED");
  const a=await login(state.a,state.usernameA,nextPassword);state.aId=a.id;
  check("ordinary-user",a.owner===false&&a.mustChangePassword===false);
  check("owner-only-users",(await api("/users",{client:state.a,status:403})).code,"ACCESS_DENIED");
  check("owner-only-audit",(await api("/audit-events",{client:state.a,status:403})).code,"ACCESS_DENIED");
  check("owner-protected",(await api("/users/"+state.ownerId,{method:"PATCH",body:{enabled:false},status:409})).code,"OWNER_PROTECTED");
  await login(state.b,state.usernameB,password);
  await api("/account/password",{client:state.b,method:"POST",body:{currentPassword:password,newPassword:nextPassword}});
  await login(state.b,state.usernameB,nextPassword);
 },
 "private-search-history":async()=>{
  await api("/watchlist/search",{client:state.a,method:"POST",body:query});
  const history=await api("/search-history",{client:state.a}),id=history.items[0]?.id;
  check("history-recorded",history.totalElements,1);
  check("history-private",(await api("/search-history",{client:state.b})).totalElements,0);
  check("history-delete-denied",(await api("/search-history/"+id,{client:state.b,method:"DELETE",status:404})).code,"NOT_FOUND");
  await api("/search-history/"+id,{client:state.a,method:"DELETE"});
  check("history-deleted",(await api("/search-history",{client:state.a})).totalElements,0);
 },
 "search-and-source-contract":async()=>{
  const sources=await api("/watchlist/sources");check("sources-configured",sources.items.map(x=>x.code).sort(),["DPL","EL"]);
  const result=await api("/watchlist/search",{method:"POST",body:{q:"",saveHistory:false}});
  check("pinned-versions",(await api("/watchlist/search",{method:"POST",body:{q:"",snapshots:result.snapshots}})).snapshots,result.snapshots);
  const csv=await request("/watchlist/exports",{client:state.owner,method:"POST",body:{q:"",snapshots:result.snapshots},text:true});
  check("csv-content-type",csv.headers.get("Content-Type").includes("text/csv")&&csv.data.includes("출처"));
  check("invalid-input",(await api("/watchlist/runs",{method:"POST",body:{source:null},status:400})).code,"SOURCE_INVALID");
  const legacy=await api("/transactions",{status:404});check("no-legacy-endpoint",legacy.code,"NOT_FOUND");
 },
 "account-revocation-and-audit":async()=>{
  await api("/users/"+state.aId,{method:"PATCH",body:{enabled:false}});
  check("deactivation-revokes",(await api("/auth/me",{client:state.a,status:401})).code,"AUTHENTICATION_REQUIRED");
  await api("/users/"+state.aId,{method:"PATCH",body:{enabled:true}});
  await api("/users/"+state.aId+"/reset-password",{method:"POST",body:{password}});
  check("reset-requires-change",(await login(state.a,state.usernameA,password)).mustChangePassword,true);
  await api("/auth/logout",{client:state.b,method:"POST"});
  check("logout-revokes",(await api("/auth/me",{client:state.b,status:401})).code,"AUTHENTICATION_REQUIRED");
  const audits=JSON.stringify(await api("/audit-events?size=100"));
  check("audit-redacted",!audits.includes(query.q)&&!audits.includes(password)&&!audits.includes(nextPassword)&&audits.includes("EXPORT_GENERATED"));
 },
 "console-product-copy":async()=>{
  const html=(await request("/login",{web:true})).data;
  check("web-ready",html.includes("트레이드옵스"));
  check("korean-language",html.includes('lang="ko"'));
  check("product-copy",!html.includes("portfolio-demo")&&!html.includes("가상 데이터 사용"));
 }
};
try{
 output=artifactPath(values.evidence);
 report={schemaVersion:1,verificationId:values["verification-id"],generatedAt:new Date().toISOString(),sourceHash:sourceHash(),scenarioHash:scenarioHash(),status:"FAILED",scenarios:definitions().map(x=>({id:x.id,status:"SKIPPED",checks:[],requests:[]}))};
 state.api=localUrl(values.api);state.web=localUrl(values.web);
 if(!ownerPassword)throw Error("VERIFICATION_CREDENTIALS_REQUIRED");
 const owner=await login(state.owner,ownerName,ownerPassword);state.ownerId=owner.id;
 if((await api("/watchlist/runs")).totalElements!==0||(await api("/users")).length!==1)throw Error("FRESH_RUNTIME_REQUIRED");
 for(const scenario of report.scenarios){current=scenario;scenario.status="FAILED";await handlers[scenario.id]();scenario.status="PASSED";console.log("PASS "+scenario.id);}
 report.status="PASSED";validateEvidence(report,{verificationId:values["verification-id"]});
}catch(error){
 if(report)report.status="FAILED";
 const safe=/^(EVIDENCE_[A-Z_]+|LOCAL_RUNTIME_REQUIRED|FRESH_RUNTIME_REQUIRED|VERIFICATION_CREDENTIALS_REQUIRED|SCENARIO_[A-Z_]+|UNEXPECTED_HTTP_STATUS|CORRELATION_ID_INVALID)$/.test(error.message)?error.message:"RUNTIME_REQUEST_FAILED";
 console.error((current?current.id+": ":"")+safe);process.exitCode=1;
}finally{if(report&&output)writeFileSync(output,JSON.stringify(report,null,2)+"\n");}
