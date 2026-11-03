import { characters, kochOrder, defaultSettings, trophies } from './data.js';
export { characters, kochOrder, defaultSettings, trophies };
const clamp = (n, lo, hi) => Math.min(hi, Math.max(lo, n));
export const speeds = [[12,5],[15,6],[15,8],[18,10],[20,12],[20,15],[20,18],[20,20],[25,25],[30,30]];
export const categories = {letters:'Lettres', digits:'Chiffres', punctuation:'Ponctuation', prosigns:'Prosignes', accents:'Accents'};
export const byLabel = label => characters.find(c => c.label === label);
export const decode = pattern => characters.find(c => c.code === pattern)?.label ?? '✱';
export const equivalent = (a,b) => a === b || (!!byLabel(a) && byLabel(a)?.code === byLabel(b)?.code);
export const kochChars = level => kochOrder.slice(0, clamp(level + 1, 2, 40));
export const levelTitle = level => level < 40 ? `Leçon Koch ${level}` : ({40:'Opérateur · mots',41:'Trafic · indicatifs',42:'QSO · phrases'}[level] || 'QSO · phrases');
export function tokens(text, unknown = false) {
  const result = [];
  const parts = String(text).toUpperCase().normalize('NFC').match(/<(?:AR|SK|KN|BT|AS)>|\s+|[\s\S]/gu) || [];
  for (const part of parts) {
    if (/^\s+$/u.test(part)) { if (result.length && result.at(-1) !== ' ') result.push(' '); }
    else if (unknown || byLabel(part)) result.push(part);
  }
  if (result.at(-1) === ' ') result.pop();
  return result;
}
export function sequence(textTokens, cw, ew) {
  const unit = 1200 / Math.max(1,cw);
  ew = Math.max(1,Math.min(cw,ew));
  const spacing = ew >= cw ? unit : (60*cw-37.2*ew)*1000/(cw*ew*19);
  return textTokens.flatMap((token,index) => {
    const code = byLabel(token)?.code;
    if (!code) return [];
    return [...code].flatMap((symbol,i) => [
      {on:true,ms:(symbol === '.' ? 1 : 3)*unit,index},
      {on:false,ms:i < code.length-1 ? unit : (textTokens[index+1] === ' ' ? 7 : 3)*spacing,index}
    ]);
  });
}
export function align(expected, answer) {
  const n = expected.length, m = answer.length;
  if (n > 1000 || m > 1000) throw new Error('La réponse est limitée à 1 000 caractères.');
  const d = Array.from({length:n+1},(_,i) => Uint16Array.from({length:m+1},(_,j) => i === 0 ? j : j === 0 ? i : 0));
  for (let i=1;i<=n;i++) for (let j=1;j<=m;j++) d[i][j] = Math.min(d[i-1][j]+1,d[i][j-1]+1,d[i-1][j-1]+(equivalent(expected[i-1],answer[j-1])?0:1));
  const correct = Array(n).fill(false), substitutions = [];
  let i=n,j=m,insertions=0;
  while (i || j) {
    if (i && j && equivalent(expected[i-1],answer[j-1]) && d[i][j] === d[i-1][j-1]) { correct[--i] = true; j--; }
    else if (i && j && d[i][j] === d[i-1][j-1]+1) { substitutions.push([expected[--i],answer[--j]]); }
    else if (i && (!j || d[i][j] === d[i-1][j]+1)) i--;
    else { insertions++; j--; }
  }
  return {correct, substitutions, insertions, accuracy:n ? correct.filter(Boolean).length/(n+insertions) : 0};
}
export const blankStats = () => ({totalChars:0,errors:0,keyTimeMs:0,recentWpm:[],trophies:{},usage:{},bestWpm:0,rxLevel:1,txLevel:1,rxStreak:0,txStreak:0,rxSpeed3:0,rxChars:{},txChars:{},series:0,trainingMs:0,days:{},history:[],confusions:{},recentRatio:[]});
export function newProfile(name) { return {format:'hermorse',version:1,name,settings:{...defaultSettings},stats:blankStats()}; }
export function readProfile(raw) {
  if (typeof raw === 'string') { if (raw.length > 5_000_000) throw new Error('Ce profil dépasse 5 Mo.'); raw=JSON.parse(raw); }
  if (!raw || raw.format !== 'hermorse' || (raw.version ?? 1) !== 1) throw new Error('Ce fichier n’est pas un profil HerMorse version 1.');
  const p = newProfile(typeof raw.name === 'string' ? raw.name.trim().slice(0,100) || 'Profil' : 'Profil');
  if (raw.settings && typeof raw.settings === 'object') {
    for (const [k,v] of Object.entries(defaultSettings)) {
      const value=raw.settings[k];
      if (typeof value === typeof v && (typeof value !== 'number' || Number.isFinite(value))) p.settings[k]=value;
    }
  }
  const limits={keyHeight:[90,340],toneHz:[300,1200],volume:[0,1],keyerWpm:[5,60],fixedWpm:[5,60],listenWpm:[5,60],farnsworthWpm:[3,60],qsoSensitivity:[0,1],qsoFreq:[300,1200]};
  for (const [key,[min,max]] of Object.entries(limits)) p.settings[key]=clamp(p.settings[key],min,max);
  p.settings.farnsworthWpm = Math.min(p.settings.listenWpm,p.settings.farnsworthWpm);
  for (const [key,allowed] of Object.entries({keySource:['virtual','external'],keyMode:['straight','iambicA','iambicB'],theme:['system','light','dark']})) {
    if (!allowed.includes(p.settings[key])) p.settings[key]=defaultSettings[key];
  }
  const s=raw.stats;
  if (s && typeof s === 'object') {
    for (const key of ['totalChars','errors','keyTimeMs','bestWpm','rxLevel','txLevel','rxStreak','txStreak','rxSpeed3','series','trainingMs']) {
      if (Number.isFinite(s[key]) && s[key]>=0 && s[key] <= Number.MAX_SAFE_INTEGER/100) p.stats[key] = key === 'bestWpm' ? s[key] : Math.floor(s[key]);
    }
    p.stats.rxLevel=clamp(p.stats.rxLevel,1,42); p.stats.txLevel=clamp(p.stats.txLevel,1,42); p.stats.rxSpeed3=clamp(p.stats.rxSpeed3,0,9);
    for (const key of ['recentWpm','recentRatio']) if (Array.isArray(s[key])) p.stats[key]=s[key].filter(v => Number.isFinite(v) && v>=0).slice(-50);
    for (const key of ['trophies','usage','days','confusions']) {
      if (s[key] && typeof s[key] === 'object' && !Array.isArray(s[key])) {
        p.stats[key]=Object.fromEntries(Object.entries(s[key]).filter(([k,v]) => k.length<150 && Number.isFinite(v) && v>=0));
      }
    }
    for (const key of ['rxChars','txChars']) {
      if (s[key] && typeof s[key] === 'object') {
        const entries=Object.entries(s[key]);
        if (entries.some(([,v]) => !Array.isArray(v) || v.length !== 2 || !v.every(Number.isSafeInteger) || v[0]<0 || v[1]<v[0])) throw new Error('Statistiques de caractères invalides.');
        p.stats[key]=Object.fromEntries(entries);
      }
    }
    if (Array.isArray(s.history)) p.stats.history=s.history.filter(r => r && ['t','l','a','w'].every(k => Number.isFinite(r[k])) && ['rx','tx'].includes(r.k)).slice(-200);
  }
  return p;
}
export class ProfileStore {
  constructor(storage) {
    this.storage=storage; this.profiles=[]; this.active=null; this.available=true; this.error='';
    try {
      const raw=storage.getItem('hermorse.profiles.v1');
      if (raw) {
        const entries=JSON.parse(raw);
        if (!Array.isArray(entries)) throw new Error('Format local invalide');
        this.profiles=entries.map(readProfile);
        if (new Set(this.profiles.map(p=>p.name)).size!==this.profiles.length) throw new Error('Profils en double');
      }
      const last=storage.getItem('hermorse.last'); this.active=this.profiles.find(p=>p.name===last)?.name ?? this.profiles[0]?.name ?? null;
    } catch { this.available=false; this.error='Le stockage des profils est inaccessible ou endommagé. Les données existantes sont conservées. Exportez vos profils avant de vider les données du navigateur.'; }
  }
  get profile() { return this.profiles.find(p=>p.name===this.active); }
  save(profiles) {
    if (!this.available) throw new Error(this.error);
    this.storage.setItem('hermorse.profiles.v1',JSON.stringify(profiles)); this.profiles=profiles;
  }
  select(name) {
    if (!this.profiles.some(p=>p.name===name)) throw new Error('Profil introuvable.');
    this.storage.setItem('hermorse.last',name); this.active=name;
  }
  create(name) {
    name=String(name).trim().slice(0,100);
    if (!name) throw new Error('Indiquez un nom de profil.');
    if (this.profiles.some(p=>p.name===name)) throw new Error('Ce profil existe déjà.');
    this.save([...this.profiles,newProfile(name)]); this.select(name);
  }
  import(raw) {
    const p=readProfile(raw), base=p.name; let i=2;
    while (this.profiles.some(other=>other.name===p.name)) { const suffix=` (${i++})`; p.name=base.slice(0,100-suffix.length)+suffix; }
    this.save([...this.profiles,p]); this.select(p.name); return p;
  }
  update(fn) {
    if (!this.profile) return;
    const p=structuredClone(this.profile); fn(p);
    this.save(this.profiles.map(other=>other.name===p.name?p:other));
  }
  remove(name) {
    this.save(this.profiles.filter(p=>p.name!==name));
    if (this.active===name) { this.active=this.profiles[0]?.name??null; this.storage.setItem('hermorse.last',this.active??''); }
  }
}
export function finish(profile,{reception,results,accuracy,cw,ew,substitutions=[]}, now=Date.now()) {
  const s=profile.stats, track=reception?'rx':'tx', oldLevel=s[`${track}Level`];
  let level=oldLevel, streak=s[`${track}Streak`], advanced=false, message='';
  if (profile.settings.guided) {
    if (accuracy<0.9) streak=0;
    else if (level<40) { level++; streak=0; advanced=true; message=level<40?`Leçon validée ! Nouveau caractère : ${kochChars(level).at(-1)}`:'Koch terminé ! Place aux mots.'; }
    else if (++streak>=3) { level=Math.min(42,level+1); streak=0; advanced=true; message=levelTitle(level); }
  }
  s[`${track}Level`]=level; s[`${track}Streak`]=streak;
  if (reception && advanced && (oldLevel<40 ? level%4===0 && s.rxSpeed3<5 : s.rxSpeed3<9)) s.rxSpeed3++;
  for (const [label,ok] of results) { const [a,b]=s[`${track}Chars`][label]??[0,0]; s[`${track}Chars`][label]=[a+Number(ok),b+1]; }
  for (const [a,b] of substitutions) s.confusions[`${a}>${b}`]=(s.confusions[`${a}>${b}`]??0)+1;
  s.series++; s.history=[...s.history,{t:now,k:track,l:oldLevel,a:accuracy,w:ew}].slice(-200);
  if (reception && accuracy>=1 && cw>=20 && ew>=20) s.trophies.vingt_wpm ??= now;
  awardTrophies(s,now); return message;
}
export function recordChar(s,wpm,known) {
  s.totalChars++; if(!known) s.errors++;
  s.recentWpm=[...s.recentWpm,wpm].slice(-50);
  if(s.recentWpm.length>=10) s.bestWpm=Math.max(s.bestWpm,s.recentWpm.slice(-10).reduce((a,b)=>a+b,0)/10);
  awardTrophies(s);
}
export function dayKey(date=new Date()) { return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`; }
export function streak(days,now=new Date()) {
  const d=new Date(now); if(!(days[dayKey(d)]>0)) d.setDate(d.getDate()-1);
  let count=0; while(days[dayKey(d)]>0) { count++; d.setDate(d.getDate()-1); } return count;
}
export function awardTrophies(s,now=Date.now()) {
  const win=(id,ok)=>{if(ok) s.trophies[id]??=now;};
  win('premier_bip',s.totalChars>0);win('km',s.rxLevel>=2||s.txLevel>=2);win('koch',s.rxLevel>=40);
  win('alphabet',characters.filter(c=>c.category==='letters').every(c=>(s.rxChars[c.label]?.[0]??0)>0));
  win('dyslexique',s.errors>50);win('dix_heures',s.trainingMs>=36_000_000);win('serie7',streak(s.days,new Date(now))>=7);
  if(s.recentWpm.length>=50){const avg=s.recentWpm.reduce((a,b)=>a+b,0)/50;win('tortue',avg<6);win('lievre',avg>=25);}
  win('platine',trophies.filter(t=>t.id!=='platine').every(t=>s.trophies[t.id]));
}
const choose=a=>a[Math.floor(Math.random()*a.length)];
export function callsign(){return choose(['F','F','ON','DL','EA','G','I','HB','OE','PA','LX'])+(1+Math.floor(Math.random()*9))+Array.from({length:choose([2,3])},()=>choose([...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'])).join('');}
export function trainingText(level,short,settings){
  let pool;
  if(!settings.guided){const flags={letters:'useLetters',digits:'useDigits',punctuation:'usePunct',prosigns:'useProsigns',accents:'useAccents'};pool=characters.filter(c=>settings[flags[c.category]]).map(c=>c.label);if(!pool.length)pool=['K','M'];}
  else if(level<40)pool=kochChars(level);
  if(pool)return Array.from({length:short?4:5},()=>Array.from({length:5},()=>settings.guided&&Math.random()<0.3?pool.at(-1):choose(pool)).join('')).join(' ');
  if(level===40)return Array.from({length:short?5:8},()=>choose(['CQ','TNX','TU','73','QTH','QRZ','QSL','RADIO','ANTENNE','BONJOUR','MERCI','NAME','RST','UR','FB','OM','ES','WX','PWR','RIG','GM','GA','BK','AGN','PSE','599'])).join(' ');
  if(level===41)return Array.from({length:short?4:6},callsign).join(' ');
  return Array.from({length:short?1:2},()=>choose([`CQ CQ CQ DE ${callsign()} K`,`NAME ${choose(['JEAN','ANNE','PAUL','MARIE'])} QTH ${choose(['PARIS','LYON','LILLE','NICE'])} UR RST 599 K`,`TNX FER QSO 73 ES GL ${callsign()} DE ${callsign()}`])).join(' ');
}

export class Keyer {
  constructor(settings,emit=()=>{}){this.s=settings;this.emit=emit;this.dit=120;this.dah=360;this.reset();}
  get unit(){return this.s.keyMode!=='straight'?1200/this.s.keyerWpm:this.s.adaptiveDecode?(this.dit+this.dah/3)/2:1200/this.s.fixedWpm;}
  reset(){this.pattern='';this.down=null;this.lastUp=null;this.phase='idle';this.dot=false;this.dash=false;this.dotMem=false;this.dashMem=false;this.last=null;this.spaceSent=true;this.emit('tone',false);this.emit('pattern','');}
  contact(dot,pressed,now){
    if(dot)this.dot=pressed;else this.dash=pressed;
    if(this.s.keyMode==='straight'){
      const held=this.dot||this.dash;
      if(held&&this.down===null){this.down=now;this.emit('tone',true);}
      else if(!held&&this.down!==null){const ms=now-this.down;this.down=null;this.lastUp=now;this.emit('tone',false);this.emit('time',ms);if(ms<12)return;
        const isDot=ms<(this.s.adaptiveDecode?(this.dit+this.dah)/2:2*this.unit);
        if(this.s.adaptiveDecode){if(isDot)this.dit=.75*this.dit+.25*ms;else this.dah=.75*this.dah+.25*ms;this.dit=clamp(this.dit,25,500);this.dah=Math.max(this.dah,2*this.dit);if(this.dah>5*this.dit)this.dit=this.dah/5;}
        this.append(isDot?'.':'-');}
    }else{this.remember();if(pressed&&this.phase==='idle'){this.last=null;this.next(now);}}
  }
  remember(){if(this.s.keyMode!=='iambicB'||this.phase==='idle')return;if(this.last==='.'&&this.dash)this.dashMem=true;if(this.last==='-'&&this.dot)this.dotMem=true;}
  next(now){const dot=this.dot||this.dotMem,dash=this.dash||this.dashMem;if(!dot&&!dash){this.phase='idle';return;}
    this.last=dot&&dash?(this.last==='.'?'-':'.'):dot?'.':'-';if(this.last==='.')this.dotMem=false;else this.dashMem=false;
    this.length=(this.last==='.'?1:3)*this.unit;this.until=now+this.length;this.phase='mark';this.remember();this.emit('tone',true);}
  tick(now){
    if(this.phase!=='idle'){this.remember();if(now>=this.until){if(this.phase==='mark'){this.emit('tone',false);this.emit('time',this.length);this.append(this.last);this.lastUp=now;this.phase='gap';this.until=now+this.unit;}else this.next(now);}if(this.phase!=='idle')return;}
    if(this.down!==null||this.lastUp===null)return;
    const gap=now-this.lastUp;
    if(this.pattern&&gap>(this.s.keyMode==='straight'?2.5:2)*this.unit)this.flush();
    else if(!this.pattern&&!this.spaceSent&&gap>6*this.unit){this.spaceSent=true;this.emit('space');}
  }
  append(symbol){this.pattern+=symbol;this.spaceSent=false;this.emit('pattern',this.pattern);if(this.pattern.length>=9)this.flush();}
  flush(){const label=decode(this.pattern);if(this.s.keyMode==='straight'&&this.s.adaptiveDecode&&this.pattern.includes('.')&&this.pattern.includes('-'))this.emit('ratio',this.dah/this.dit);this.pattern='';this.emit('pattern','');this.emit('char',{label,wpm:1200/this.unit,known:label!=='✱'});}
}
