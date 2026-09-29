//go:build linux

package platform

import "testing"

func TestParseCPUList(t *testing.T) {
	got, err := parseCPUList("0-2,4,6-7")
	if err != nil {
		t.Fatal(err)
	}
	want := []int{0, 1, 2, 4, 6, 7}
	if len(got) != len(want) {
		t.Fatalf("got %v want %v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("got %v want %v", got, want)
		}
	}
}
