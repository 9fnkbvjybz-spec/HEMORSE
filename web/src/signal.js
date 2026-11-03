import { decode } from './core.js';
export class CWDecoder {
  constructor(emit=()=>{}){this.emit=emit;this.dit=100;this.dah=300;this.pattern='';this.spaceSent=true;}
  get unit(){return (this.dit+this.dah/3)/2;}
  mark(ms){if(ms<20)return;const dot=ms<(this.dit+this.dah)/2;
    if(dot)this.dit=.8*this.dit+.2*ms;else this.dah=.8*this.dah+.2*ms;
    this.dit=Math.min(600,Math.max(20,this.dit));this.dah=Math.max(this.dah,2*this.dit);if(this.dah>5*this.dit)this.dit=this.dah/5;
    this.pattern+=dot?'.':'-';this.spaceSent=false;if(this.pattern.length>8)this.flush();}
  silence(ms){if(this.pattern&&ms>2*this.unit)this.flush();else if(!this.pattern&&!this.spaceSent&&ms>5*this.unit){this.spaceSent=true;this.emit('space');}}
  flush(){this.emit('char',decode(this.pattern));this.pattern='';}
}
export class Resampler {
  constructor(){this.index=-1;this.next=0;this.previous=0;}
  process(samples,rate){const out=[];if(rate<8000)return out;for(const sample of samples){this.index++;while(this.next<=this.index){out.push(this.index===0?sample:this.previous+(sample-this.previous)*(this.next-this.index+1));this.next+=rate/8000;}this.previous=sample;}return out;}
}
export class SignalAnalyzer {
  constructor(emit=()=>{}){
    this.emit=emit;this.frequency=600;this.afc=true;this.sensitivity=.5;this.pending=[];this.ring=new Float64Array(512);this.pos=0;
    this.floor=.0001;this.peak=.0001;this.keyed=false;this.stateMs=0;this.blocks=0;this.candidate=0;this.candidateCount=0;this.decoder=new CWDecoder(emit);
  }
  feed(samples){this.pending.push(...samples);while(this.pending.length>=80)this.block(this.pending.splice(0,80));}
  block(samples){
    const coeff=2*Math.cos(2*Math.PI*this.frequency/8000);let s1=0,s2=0;
    for(const x of samples){this.ring[this.pos]=x;this.pos=(this.pos+1)%512;const s=x+coeff*s1-s2;s2=s1;s1=s;}
    const mag=Math.sqrt(Math.max(0,s1*s1+s2*s2-coeff*s1*s2))/80;
    this.floor+=(mag-this.floor)*(mag<this.floor?.1:.005);this.peak=mag>this.peak?mag:this.peak-(this.peak-this.floor)*.004;
    const span=this.peak-this.floor,k=.65-.4*this.sensitivity,signal=this.peak>this.floor*4&&this.peak>.0002;
    const on=signal&&mag>this.floor+span*k*(this.keyed?.6:1);
    if(on===this.keyed){this.stateMs+=10;if(!this.keyed)this.decoder.silence(this.stateMs);}else{if(this.keyed)this.decoder.mark(this.stateMs);this.keyed=on;this.stateMs=10;}
    if(++this.blocks%10)return;
    const bins=[];
    for(let b=19;b<=76;b++){let re=0,im=0;for(let i=0;i<512;i++){const x=this.ring[(this.pos+i)%512]*(.5-.5*Math.cos(2*Math.PI*i/511)),a=2*Math.PI*b*i/512;re+=x*Math.cos(a);im-=x*Math.sin(a);}bins.push(Math.hypot(re,im));}
    const maximum=Math.max(...bins),i=bins.indexOf(maximum),median=Math.max(1e-9,[...bins].sort((a,b)=>a-b)[bins.length>>1]);
    if(this.afc&&maximum/median>6&&i>0&&i<bins.length-1){const a=bins[i-1],b=bins[i],c=bins[i+1],denom=a-2*b+c,f=(i+19+(Math.abs(denom)>1e-12?.5*(a-c)/denom:0))*8000/512;
      if(Math.abs(f-this.candidate)<20)this.candidateCount++;else{this.candidate=f;this.candidateCount=1;}
      if(this.candidateCount>=3&&Math.abs(f-this.frequency)>10)this.frequency=Math.min(1200,Math.max(300,f));}
    this.emit('status',{frequency:this.frequency,wpm:1200/this.decoder.unit,keyed:this.keyed,signal,level:Math.min(1,Math.max(0,mag/(this.peak+1e-9))),spectrum:bins.map(v=>v/Math.max(1e-6,maximum))});
  }
}
