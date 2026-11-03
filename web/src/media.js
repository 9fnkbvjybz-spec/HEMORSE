import { sequence } from './core.js';
export class MediaEngine {
  constructor(){this.id=0;this.microId=0;this.pending=null;this.settings={};}
  async ensure(settings){
    this.settings={...settings};
    const Context=globalThis.AudioContext||globalThis.webkitAudioContext;
    if(!Context)throw new Error('Le navigateur ne prend pas en charge l’audio requis. Ouvrez HerMorse dans une version récente de Safari.');
    if(!this.context){this.context=new Context({latencyHint:'interactive'});this.init=this.context.audioWorklet.addModule('/tone-worklet.js').then(()=>{
      this.node=new AudioWorkletNode(this.context,'hermorse-tone',{numberOfInputs:0,numberOfOutputs:1,outputChannelCount:[1]});this.node.connect(this.context.destination);
      this.node.port.onmessage=({data})=>{if(data.id!==this.id||!this.pending)return;if(data.type==='progress')this.pending.progress(data.index);if(data.type==='done'){const p=this.pending;this.pending=null;p.resolve(true);}};
    });}
    await this.context.resume();await this.init;
    this.node.port.postMessage({type:'settings',frequency:settings.toneHz,volume:settings.volume});
  }
  key(on){this.node?.port.postMessage({type:'key',on:on&&this.settings.sidetone});}
  async play(textTokens,cw,ew,settings,progress=()=>{}){
    this.cancel();const id=this.id;
    await this.ensure(settings);if(id!==this.id)return false;
    return new Promise(resolve=>{this.pending={resolve,progress};this.node.port.postMessage({type:'sequence',segments:sequence(textTokens,cw,ew),id});});
  }
  cancel(){this.id++;if(this.pending){this.pending.resolve(false);this.pending=null;}this.node?.port.postMessage({type:'stop',id:this.id});}
  async startMicro(settings,onEvent){
    this.stopMicro();this.cancel();const id=this.microId;
    if(!globalThis.isSecureContext||!navigator.mediaDevices?.getUserMedia)throw new Error('Le microphone nécessite une adresse HTTPS et un navigateur compatible.');
    const Context=globalThis.AudioContext||globalThis.webkitAudioContext;
    const context=new Context();this.microContext=context;
    try{
      await context.resume();
      const stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:false,noiseSuppression:false,autoGainControl:false},video:false});
      if(id!==this.microId){stream.getTracks().forEach(t=>t.stop());return false;}
      this.stream=stream;
      await context.audioWorklet.addModule('/capture-worklet.js');
      if(id!==this.microId)return false;
      const source=context.createMediaStreamSource(stream),node=new AudioWorkletNode(context,'hermorse-capture');
      this.capture=node;node.port.onmessage=({data})=>{if(id===this.microId)onEvent(data.type,data.value);};
      this.configureMicro(settings);source.connect(node);node.connect(context.destination);
      stream.getAudioTracks()[0].addEventListener('ended',()=>{if(id===this.microId){this.stopMicro();onEvent('ended');}},{once:true});return true;
    }catch(error){if(id===this.microId)this.stopMicro();throw new Error(error.name==='NotAllowedError'?'Autorisez le microphone dans Safari pour écouter le poste.':`Microphone indisponible : ${error.message}`);}
  }
  configureMicro(settings){this.capture?.port.postMessage({qsoFreq:settings.qsoFreq,qsoAfc:settings.qsoAfc,qsoSensitivity:settings.qsoSensitivity});}
  stopMicro(){this.microId++;this.stream?.getTracks().forEach(t=>t.stop());this.stream=null;this.capture?.disconnect();this.capture=null;this.microContext?.close().catch(()=>{});this.microContext=null;}
  stop(){this.cancel();this.key(false);this.stopMicro();}
}
