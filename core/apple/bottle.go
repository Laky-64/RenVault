package apple

type BottleInfo struct {
	Index      int    `json:"index"`
	Name       string `json:"name"`
	Model      string `json:"model"`
	ShortModel string `json:"shortModel"`
	Class      string `json:"class"`
	Serial     string `json:"serial"`
	Build      string `json:"build"`
	OS         string `json:"os"`
	OSVersion  string `json:"osVersion"`
	ImageURL   string `json:"imageURL"`
}
