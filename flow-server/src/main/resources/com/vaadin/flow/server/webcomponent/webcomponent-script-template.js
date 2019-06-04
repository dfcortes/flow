class _TagCamel_ extends HTMLElement {
  constructor() {
	super();
	this._propertyUpdatedFromServer = {};
	this.$ = {};
	this.initDefaults();
	var shadow = this.attachShadow( { mode: 'open' } );
	var style = document.createElement("style");
    style.innerHTML = `
      :host {
        display: inline-block;
      }
    `;
	shadow.appendChild(style);
	shadow.appendChild(document.createElement("slot"));
  }

  _PropertyMethods_

  initDefaults() {
	  _PropertyDefaults_
  }

  _sync(property, newValue) {
    if (this.$server) {
      if (!this._propertyUpdatedFromServer[property]) {
        this.$server.sync(property, newValue);
      } else {
        this._propertyUpdatedFromServer[property] = false;
      }
    }
  }

  _updatePropertyFromServer(property, newValue) {
    if (this.__proto__.hasOwnProperty(property)) {
      this._propertyUpdatedFromServer[property] = true;
      this[property] = newValue;
    }
  }

  connectedCallback() {
    if (super.connectedCallback) {
	  super.connectedCallback();
    }


    this._propertyUpdatedFromServer = {};
    this.$ = {};
    this._connect();
  }

  _connect(){
      if (!this.$.id) {
        this._registerElement();
      } else {
          console.debug('reconnecting '+this+' using id '+this.$.id);
          this.$server.reconnect();
      }
  }

  _registerElement() {
    this.$.id = "_TagCamel_-" + _TagCamel_.id++;
    console.debug('registering '+this+' using id '+this.$.id);
    const flowRoot = document.body;
    
    const poller = () => {
  	  var flowClient = Object.values(window.Vaadin.Flow.clients).find(c => c.exportedWebComponents && c.exportedWebComponents.indexOf('_TagDash_') != -1);
      if (flowClient && flowClient.connectWebComponent) {
    	flowRoot.$ = flowRoot.$ || {};
        flowRoot.$[this.$.id] = this;
        flowClient.connectWebComponent({tag: '_TagDash_', id: this.$.id});
        console.debug('connected '+this+' using id '+this.$.id);
      } else {
        setTimeout(poller, 10);
      }
    };

    poller();
  }

  disconnectedCallback() {
    this.$server.disconnected();

    console.log('disconnected', this);
  }

  serverConnected() {
	_PropertySync_
  }
}

_TagCamel_.id = 0;
customElements.define('_TagDash_', _TagCamel_);
