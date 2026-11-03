import { SignalAnalyzer, Resampler } from './src/signal.js';
class MorseCapture extends AudioWorkletProcessor {
  constructor(){super();this.resampler=new Resampler();this.analyzer=new SignalAnalyzer((type,value)=>this.port.postMessage({type,value}));
    this.port.onmessage=({data})=>{this.analyzer.frequency=data.qsoFreq;this.analyzer.afc=data.qsoAfc;this.analyzer.sensitivity=data.qsoSensitivity;};}
  process(inputs,outputs){const input=inputs[0]?.[0];if(input)this.analyzer.feed(this.resampler.process(input,sampleRate));for(const channel of outputs[0]||[])channel.fill(0);return true;}
}
registerProcessor('hermorse-capture',MorseCapture);
