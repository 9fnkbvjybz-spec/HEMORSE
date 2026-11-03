import test from 'node:test';
import assert from 'node:assert/strict';
import { characters, kochOrder, defaultSettings, byLabel, decode, tokens, equivalent, sequence, align, newProfile, readProfile, ProfileStore, finish, trainingText, Keyer, streak, awardTrophies } from '../src/core.js';
import { CWDecoder, SignalAnalyzer, Resampler } from '../src/signal.js';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
const storage = () => { const data = new Map(); return {getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v)}; };
test('same Android alphabet, prosign priority and Koch order',()=>{
  assert.equal(characters.length,60); assert.equal(kochOrder.length,40); assert.equal(new Set(kochOrder).size,40);
  assert(kochOrder.every(c=>byLabel(c)));assert.equal(decode('.-.-.'),'<AR>');assert(equivalent('=','<BT>'));assert(!equivalent('K','M'));
});
test('prosigns are atomic, whitespace normalized, unknown answers preserved',()=>{
  assert.deepEqual(tokens(' cq  <ar> É '),['C','Q',' ','<AR>',' ','É']);assert.deepEqual(tokens('A%',true),['A','%']);
});
test('PARIS duration obeys standard and Farnsworth WPM',()=>{
  for(const [cw,ew] of [[20,20],[20,5],[12,5],[30,30]]){
    const total=sequence(['P','A','R','I','S',' '],cw,ew).reduce((sum,s)=>sum+s.ms,0);
    assert(Math.abs(total-60000/ew)<.001);
  }
});
test('each Koch exercise only contains learned characters',()=>{
  for(let level=1;level<40;level++){
    const t=tokens(trainingText(level,false,defaultSettings)).filter(t=>t!==' ');
    assert.equal(t.length,25);assert(t.every(c=>kochOrder.slice(0,level+1).includes(c)));
  }
});
test('custom exercises support prosigns and empty-pool fallback',()=>{
  const settings={...defaultSettings,guided:false,useLetters:false,useDigits:false,useProsigns:true};
  const t=tokens(trainingText(1,true,settings)).filter(t=>t!==' ');assert.equal(t.length,20);assert(t.every(c=>byLabel(c).category==='prosigns'));
  settings.useProsigns=false;assert(tokens(trainingText(1,true,settings)).every(c=>['K','M',' '].includes(c)));
});
test('alignment handles missing characters, aliases, and penalizes added guesses',()=>{
  assert.deepEqual(align(['K','M','R'],['K','R']).correct,[true,false,true]);
  assert(align(['K','M'],['K','M','X']).accuracy<1);assert.equal(align(['<BT>'],['=']).accuracy,1);
  assert.equal(align([],[]).accuracy,0);assert.throws(()=>align(['K'],Array(1001).fill('M')));
});
test('minimal Android profile has all defaults',()=>{
  assert.deepEqual(readProfile('{"format":"hermorse","version":1,"name":"Camille","settings":{},"stats":{}}'),newProfile('Camille'));
});
test('Android JSON format preserves speed, stats arrays and millisecond dates',()=>{
  const p=readProfile({format:'hermorse',version:1,name:'Élodie',settings:{keyMode:'iambicB'},stats:{rxSpeed3:4,rxChars:{K:[8,10]},trophies:{km:1700000000000},history:[{t:1700000000000,k:'rx',l:1,a:.9,w:12}]}});
  assert.equal(p.stats.rxSpeed3,4);assert.deepEqual(p.stats.rxChars.K,[8,10]);assert.equal(p.settings.keyMode,'iambicB');
  assert.deepEqual(readProfile(JSON.stringify(p)),p);
});
test('invalid files, newer versions and malformed stats are rejected',()=>{
  assert.throws(()=>readProfile('not json'));assert.throws(()=>readProfile({format:'other'}));assert.throws(()=>readProfile({format:'hermorse',version:2}));
  assert.throws(()=>readProfile({format:'hermorse',stats:{rxChars:{K:[3]}}}));
});
test('imported values are clamped and types validated',()=>{
  const p=readProfile({format:'hermorse',settings:{keyerWpm:0,volume:9,listenWpm:10,farnsworthWpm:50,keyMode:'nonsense'},stats:{rxLevel:500,rxSpeed3:99}});
  assert.equal(p.settings.keyerWpm,5);assert.equal(p.settings.volume,1);assert.equal(p.settings.farnsworthWpm,10);assert.equal(p.settings.keyMode,'straight');assert.equal(p.stats.rxLevel,42);assert.equal(p.stats.rxSpeed3,9);
});
test('profile create/import collision/select/delete persists across reloads',()=>{
  const mem=storage(),store=new ProfileStore(mem);store.create('Paul');store.import(newProfile('Paul'));store.import(newProfile('Paul'));
  assert.deepEqual(store.profiles.map(p=>p.name),['Paul','Paul (2)','Paul (3)']);
  store.select('Paul');store.update(p=>p.stats.series=3);assert.equal(new ProfileStore(mem).profile.stats.series,3);
  store.remove('Paul');assert.equal(new ProfileStore(mem).profile.name,'Paul (2)');
});
test('storage write failures do not change in-memory profiles',()=>{
  const mem=storage(),store=new ProfileStore(mem);store.create('Paul');
  mem.setItem=()=>{throw new Error('quota');};assert.throws(()=>store.update(p=>p.stats.series=99));assert.equal(store.profile.stats.series,0);
});
test('very long duplicate profile names remain reloadable',()=>{
  const mem=storage(),store=new ProfileStore(mem),name='A'.repeat(100);store.create(name);store.import(newProfile(name));
  const restored=new ProfileStore(mem);assert(restored.available);assert.equal(restored.profiles.length,2);assert(restored.profiles.every(p=>p.name.length<=100));
});
test('corrupt storage is preserved and never replaced with defaults',()=>{
  const mem=storage();mem.setItem('hermorse.profiles.v1','broken');const store=new ProfileStore(mem);
  assert.equal(store.available,false);assert.throws(()=>store.create('Paul'));assert.equal(mem.getItem('hermorse.profiles.v1'),'broken');
});
test('90% advances one track; phases require three consecutive passes',()=>{
  const p=newProfile('Paul');const done=(reception,accuracy)=>finish(p,{reception,results:[['K',true]],accuracy,cw:12,ew:5});
  done(true,.9);assert.equal(p.stats.rxLevel,2);assert.equal(p.stats.txLevel,1);assert.equal(p.stats.history[0].l,1);
  p.stats.rxLevel=40;done(true,1);done(true,1);done(true,.89);assert.equal(p.stats.rxStreak,0);
  for(let i=0;i<3;i++)done(true,.9);assert.equal(p.stats.rxLevel,41);assert.equal(p.stats.rxStreak,0);
});
test('speed ladder and custom mode progression',()=>{
  const p=newProfile('Paul');p.stats.rxLevel=3;finish(p,{reception:true,results:[],accuracy:1,cw:12,ew:5});assert.equal(p.stats.rxSpeed3,1);
  p.settings.guided=false;finish(p,{reception:true,results:[],accuracy:1,cw:12,ew:5});assert.equal(p.stats.rxLevel,4);
});
test('trophy awards and days streak',()=>{
  const p=newProfile('Paul');p.stats.totalChars=1;p.stats.errors=51;awardTrophies(p.stats);assert(p.stats.trophies.premier_bip);assert(p.stats.trophies.dyslexique);assert(!p.stats.trophies.live);
  assert.equal(streak({'2026-10-24':1,'2026-10-25':1,'2026-10-26':1},new Date(2026,9,26,12)),3);
});
test('straight-key duration decoder produces SOS, then a space',()=>{
  const events=[],k=new Keyer({...defaultSettings,adaptiveDecode:false,fixedWpm:12},(type,v)=>events.push([type,v]));let time=0;
  for(const code of ['...','---','...']){for(const mark of code){k.contact(true,true,time);time+=mark==='.'?100:300;k.contact(true,false,time);time+=100;}time+=201;k.tick(time);}
  k.tick(time+700);assert.deepEqual(events.filter(e=>e[0]==='char').map(e=>e[1].label),['S','O','S']);assert(events.some(e=>e[0]==='space'));
});
test('iambic B retains opposite paddle while A does not',()=>{
  for(const mode of ['iambicA','iambicB']){const patterns=[],k=new Keyer({...defaultSettings,keyMode:mode,keyerWpm:12},(t,v)=>{if(t==='pattern'&&v)patterns.push(v);});
    k.contact(true,true,0);k.contact(false,true,10);k.contact(true,false,50);k.contact(false,false,50);for(let t=100;t<=800;t+=100)k.tick(t);
    assert.equal(patterns.at(-1),mode==='iambicB'?'.-':'.');}
});
test('reset cancels a held key without an unexpected character',()=>{
  const events=[],k=new Keyer(defaultSettings,(t,v)=>events.push([t,v]));k.contact(true,true,0);k.reset();k.tick(10000);
  assert(events.some(([t,v])=>t==='tone'&&!v));assert(!events.some(([t])=>t==='char'));
});
test('streaming resampling is independent of buffer boundaries',()=>{
  const input=Array.from({length:4800},(_,i)=>Math.sin(i*.1)),a=new Resampler(),b=new Resampler();const full=a.process(input,48000),parts=[];
  for(let i=0;i<input.length;i+=113)parts.push(...b.process(input.slice(i,i+113),48000));assert.deepEqual(parts,full);assert.equal(full.length,800);
});
test('silence and short spikes do not create characters',()=>{
  const events=[],d=new CWDecoder((t,v)=>events.push([t,v]));d.mark(5);d.silence(1000);assert.equal(events.length,0);
  const a=new SignalAnalyzer((t,v)=>events.push([t,v]));a.feed(Array(16000).fill(0));assert(!events.some(([t])=>t==='char'));assert.equal(events.at(-1)[1].signal,false);
});
test('synthetic audio is actually decoded as SOS',()=>{
  const labels=[],a=new SignalAnalyzer((t,v)=>{if(t==='char')labels.push(v);});a.afc=false;let phase=0;const audio=Array(4000).fill(0);
  for(const segment of sequence(['S','O','S'],12,12)){for(let i=0;i<segment.ms*8;i++){audio.push(segment.on?.7*Math.sin(phase):0);phase+=2*Math.PI*600/8000;}}
  audio.push(...Array(8000).fill(0));for(let i=0;i<audio.length;i+=137)a.feed(audio.slice(i,i+137));assert.deepEqual(labels,['S','O','S']);
});
test('automatic frequency tracking locates a 740 Hz tone',()=>{
  let frequency=0;const a=new SignalAnalyzer((t,v)=>{if(t==='status')frequency=v.frequency;});
  for(let i=0;i<16000;i+=512)a.feed(Array.from({length:Math.min(512,16000-i)},(_,j)=>.7*Math.sin(2*Math.PI*740*(i+j)/8000)));
  assert(Math.abs(frequency-740)<10);
});
test('actual oscillator worklet produces samples, completes playback, and stops cleanly',()=>{
  let Processor;const messages=[];
  class Base { constructor(){this.port={postMessage:m=>messages.push(m)};} }
  runInNewContext(readFileSync(new URL('../public/tone-worklet.js',import.meta.url),'utf8'),{AudioWorkletProcessor:Base,sampleRate:48000,registerProcessor:(name,type)=>Processor=type});
  const oscillator=new Processor();oscillator.port.onmessage({data:{type:'sequence',id:1,segments:sequence(['A'],20,20)}});
  let energy=0;
  for(let i=0;i<400;i++){const channel=new Float32Array(128);oscillator.process([],[[channel]]);energy+=channel.reduce((s,v)=>s+v*v,0);}
  assert(energy>100);assert(messages.some(m=>m.type==='done'&&m.id===1));
  oscillator.port.onmessage({data:{type:'key',on:true}});oscillator.port.onmessage({data:{type:'stop',id:2}});
  for(let i=0;i<10;i++)oscillator.process([],[[new Float32Array(128)]]);
  const silence=new Float32Array(128);oscillator.process([],[[silence]]);assert(silence.every(v=>v===0));assert.equal(oscillator.playing,false);
});
